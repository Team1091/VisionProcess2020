/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionThread;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public final class Version1 {

    private final int processedImageStreamPort = 1182;
    private List<VideoSource> cameras;

    public int team;
    public boolean server;
    public List<CameraConfig> cameraConfigs;

    public Version1(int team, boolean server, List<CameraConfig> cameraConfigs) {
        this.team = team;
        this.server = server;
        this.cameraConfigs = cameraConfigs;
        this.cameras = cameras = new ArrayList<>();
    }

    /**
     * run.
     */
    public void run() {
        // start NetworkTables
        NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
        if (server) {
            System.out.println("Setting up NetworkTables server");
            ntinst.startServer();
        } else {
            System.out.println("Setting up NetworkTables client for team " + team);
            ntinst.startClientTeam(team);
        }

        // start cameras
        for (CameraConfig config : cameraConfigs) {
            cameras.add(startCamera(config));
        }

        PipelineWrapper pipeline = new PipelineWrapper();

        // start image processing on camera 0 if present
        if (cameras.size() >= 1) {
            var camera = cameras.get(0);
            var imageSource = startOutput(camera);
            VisionThread visionThread = new VisionThread(camera, pipeline, pi -> {
                //UpdateContours(pi, ntinst.getTable("SNIP/myContoursReport"), imageSource);
                UpdateLargestContour(pi, ntinst.getTable("SNIP/myContoursReport"), imageSource);
            });
            visionThread.start();
        }

        // loop forever
        for (; ; ) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    private void UpdateLargestContour(PipelineWrapper pi, NetworkTable contoursNetworkTable, CvSource imageSource){
        try {
            ArrayList<MatOfPoint> contours = pi.findContoursOutput();
            Mat outputImage = pi.getSourceImage();
            double[] areas = new double[1];
            double[] centerXs = new double[1];
            double[] centerYs = new double[1];
            double[] widths = new double[1];
            double[] heights = new double[1];
            int largestContour = -1;
            for (int i = 0; i < contours.size(); i++) {
                if(largestContour == -1){
                    largestContour = i;
                }
                var areaNew = Imgproc.contourArea(contours.get(i));
                var areaOld = Imgproc.contourArea(contours.get(largestContour));
                if(areaNew > areaOld){
                    largestContour = i;
                }
            }

            if(largestContour > -1){
                Imgproc.drawContours(outputImage, contours, largestContour, new Scalar(255, 255, 255), 1);
                var req = Imgproc.boundingRect(contours.get(largestContour));
                areas[0] = Imgproc.contourArea(contours.get(largestContour));
                centerXs[0] = req.x;  // Not wrong
                centerYs[0] = req.y; // Not wrong
                widths[0] = req.width;
                heights[0] = req.height;
            }

            imageSource.putFrame(outputImage);
            contoursNetworkTable.getEntry("area").setDoubleArray(areas);
            contoursNetworkTable.getEntry("centerX").setDoubleArray(centerXs);
            contoursNetworkTable.getEntry("centerY").setDoubleArray(centerYs);
            contoursNetworkTable.getEntry("width").setDoubleArray(widths);
            contoursNetworkTable.getEntry("height").setDoubleArray(heights);
        } catch (Exception e) {
            System.out.println("Call to UpdateContours failed!");
            throw e;
        }
    }

    private void UpdateContours(PipelineWrapper pi, NetworkTable contoursNetworkTable, CvSource imageSource) {
        try {
            ArrayList<MatOfPoint> contours = pi.findContoursOutput();
            Mat outputImage = pi.getSourceImage();
            double[] areas = new double[contours.size()];
            double[] centerXs = new double[contours.size()];
            double[] centerYs = new double[contours.size()];
            double[] widths = new double[contours.size()];
            double[] heights = new double[contours.size()];
            int largestContour = -1;
            for (int i = 0; i < contours.size(); i++) {
                Imgproc.drawContours(outputImage, contours, i, new Scalar(255, 255, 255), 1);
                var req = Imgproc.boundingRect(contours.get(i));
                areas[i] = Imgproc.contourArea(contours.get(i));
                centerXs[i] = req.x;  // Not wrong
                centerYs[i] = req.y; // Not wrong
                widths[i] = req.width;
                heights[i] = req.height;
            }

            imageSource.putFrame(outputImage);
            contoursNetworkTable.getEntry("area").setDoubleArray(areas);
            contoursNetworkTable.getEntry("centerX").setDoubleArray(centerXs);
            contoursNetworkTable.getEntry("centerY").setDoubleArray(centerYs);
            contoursNetworkTable.getEntry("width").setDoubleArray(widths);
            contoursNetworkTable.getEntry("height").setDoubleArray(heights);
        } catch (Exception e) {
            System.out.println("Call to UpdateContours failed!");
            throw e;
        }
    }

    private CvSource startOutput(VideoSource camera) {
        CameraServer srv = CameraServer.getInstance();
        MjpegServer cvStream = new MjpegServer("CV Image Stream", processedImageStreamPort);
        CvSource imageSource = new CvSource("CV Image Source", VideoMode.PixelFormat.kMJPEG, camera.getVideoMode().width, camera.getVideoMode().height, camera.getVideoMode().fps);
        cvStream.setSource(imageSource);
        cvStream.setFPS(15);
        cvStream.setCompression(25);
        srv.addServer(cvStream);

        return imageSource;
    }

    /**
     * Start running the camera.
     */
    private VideoSource startCamera(CameraConfig config) {
        System.out.println("Starting camera '" + config.name + "' on " + config.path);
        CameraServer inst = CameraServer.getInstance();
        UsbCamera camera = new UsbCamera(config.name, config.path);
        MjpegServer server = inst.startAutomaticCapture(camera);

        Gson gson = new GsonBuilder().create();

        camera.setConfigJson(gson.toJson(config.config));
        camera.setConnectionStrategy(VideoSource.ConnectionStrategy.kKeepOpen);

        if (config.streamConfig != null) {
            server.setConfigJson(gson.toJson(config.streamConfig));
        }

        return camera;
    }
}
