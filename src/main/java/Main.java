import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.opencv.core.*;

public class Main {
    private static String configFile = "/boot/frc.json";
    public static int team;
    public static boolean server;
    public static List<CameraConfig> cameraConfigs = new ArrayList<>();
    public static List<SwitchedCameraConfig> switchedCameraConfigs = new ArrayList<>();
    public static List<VideoSource> cameras = new ArrayList<>();

    public static void main(String... args) {
        Main main = new Main();
        if (args.length > 0) {
            configFile = args[0];
        }

        // read configuration
        if (!readConfig()) {
            return;
        }
        main.run();
    }

    public void run() {
        // Loads our OpenCV library. This MUST be included
        //System.loadLibrary("opencv_java310");

        NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
        System.out.println("Setting up NetworkTables client for team " + 1091);
        ntinst.startClientTeam(1091);


        // This is the network port you want to stream the raw received image to
        // By rules, this has to be between 1180 and 1190, so 1185 is a good choice
        int streamPort = 1185;

        // This streaming mjpeg server will allow you to see the source image in a browser.
        CameraServer inst = CameraServer.getInstance();
       // MjpegServer inputStream = new MjpegServer("MJPEG Server", streamPort);

        // HTTP Camera
        // This is our camera name from the robot.
        // This can be set in your robot code with the following command
        // CameraServer.getInstance().startAutomaticCapture("YourCameraNameHere");
        // "USB Camera 0" is the default if no string is specified
        // In NetworkTables, you can create a key CameraPublisher/<YourCameraNameHere>/streams
        // of an array of strings to store the urls of the stream(s) the camera publishes.
        // These urls point to an mjpeg stream over http, with each jpeg image separated
        // into multiparts with the mixed data sub-type.
        // See https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html for more info.
        // Jpeg part delimiters are separated by a boundary string specified in the Content-Type header.
        //String cameraName = "USB Camera 0";
        String cameraName = "VisionCoProc";
        System.out.println("THIS IS THE CAMERA PATH... :"+cameraConfigs.get(0).path);
//        UsbCamera camera = setHttpCamera(cameraName, inputStream, cameraConfigs.get(0).path);
        UsbCamera camera = new UsbCamera(cameraName, cameraConfigs.get(0).path);
        MjpegServer inputStream = inst.startAutomaticCapture(camera);

        /***********************************************/

        // This creates a CvSink for us to use. This grabs images from our selected camera,
        // and will allow us to use those images in opencv
        CvSink imageSink = new CvSink("CV Image Grabber");
        imageSink.setSource(camera);

        // This creates a CvSource to use.
        // This will take in a Mat image that has had OpenCV operations.
        CvSource imageSource = new CvSource(
                "CV Image Source",
                VideoMode.PixelFormat.kMJPEG,
                camera.getVideoMode().width,
                camera.getVideoMode().height,
                camera.getVideoMode().fps);
        // This streaming mjpeg server will allow you to see the final image processed image in a browser.
        CameraServer srv = CameraServer.getInstance();
        MjpegServer cvStream = new MjpegServer("CV Image Stream", 1186);
        cvStream.setSource(imageSource);
        srv.addServer(cvStream);

        // Set up the image pump to grab images.
        ImagePump imagePump = new ImagePump(imageSink);

        GripPipeline pipeline = new GripPipeline();
        NetworkTable publishingTable = ntinst.getTable("SNIP/myNetworkTable");
        // Get image processing components.
        ImageProcessor gripImageProcessor = new ImageProcessor(pipeline, new NetworkTableWriter(new GripPipelineInterpreter(pipeline), publishingTable));

        // Init these vars outside processing loop, as they are expensive to create.
        Mat inputImage = new Mat();
        Mat outputImage = new Mat();

        System.out.println("Processing stream...");

        // Prime the image pump
        inputImage = imagePump.pump();

        while (!Thread.currentThread().isInterrupted()) {
            if (!inputImage.empty()) {
                // Process the image looking for respective color balls...concurrently
                // ProcessAsync chews on the image and writes to
                // asynchronously.  Also, pump the frame grabber for the next frame.
                gripImageProcessor.processAsync(inputImage);
                imagePump.pumpAsync();

                // Await image processing to finsh
                gripImageProcessor.awaitProcessCompletion();

                // Annotate the image
                outputImage = gripImageProcessor.annotate(outputImage);

                // Write out the image
                //imageSource.putFrame(outputImage);

                // Get the next image
                inputImage = imagePump.awaitPumpCompletion();
            } else {
                // Get the next image, because the prior one was empty
                inputImage = imagePump.pump();
            }
        }
    }


    private HttpCamera setHttpCamera(String cameraName, MjpegServer server, String cameraURL) {
        // If the camera URL is explicitly specified on the command line, then use it.
        HttpCamera camera = null;
        camera = new HttpCamera(cameraName, cameraURL);
        server.setSource(camera);
        return camera;
    }

    public static void parseError(String str) {
        System.err.println("config error in '" + configFile + "': " + str);
    }

    public static boolean readCameraConfig(JsonObject config) {
        CameraConfig cam = new CameraConfig();

        // name
        JsonElement nameElement = config.get("name");
        if (nameElement == null) {
            parseError("could not read camera name");
            return false;
        }
        cam.name = nameElement.getAsString();

        // path
        JsonElement pathElement = config.get("path");
        if (pathElement == null) {
            parseError("camera '" + cam.name + "': could not read path");
            return false;
        }
        cam.path = pathElement.getAsString();

        // stream properties
        cam.streamConfig = config.get("stream");

        cam.config = config;

        cameraConfigs.add(cam);
        return true;
    }

    /**
     * Read single switched camera configuration.
     */
    public static boolean readSwitchedCameraConfig(JsonObject config) {
        SwitchedCameraConfig cam = new SwitchedCameraConfig();

        // name
        JsonElement nameElement = config.get("name");
        if (nameElement == null) {
            parseError("could not read switched camera name");
            return false;
        }
        cam.name = nameElement.getAsString();

        // path
        JsonElement keyElement = config.get("key");
        if (keyElement == null) {
            parseError("switched camera '" + cam.name + "': could not read key");
            return false;
        }
        cam.key = keyElement.getAsString();

        switchedCameraConfigs.add(cam);
        return true;
    }

    public static boolean readConfig() {
        // parse file
        JsonElement top;
        try {
            top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
        } catch (IOException ex) {
            System.err.println("could not open '" + configFile + "': " + ex);
            return false;
        }

        // top level must be an object
        if (!top.isJsonObject()) {
            parseError("must be JSON object");
            return false;
        }
        JsonObject obj = top.getAsJsonObject();

        // team number
        JsonElement teamElement = obj.get("team");
        if (teamElement == null) {
            parseError("could not read team number");
            return false;
        }
        team = teamElement.getAsInt();

        // ntmode (optional)
        if (obj.has("ntmode")) {
            String str = obj.get("ntmode").getAsString();
            if ("client".equalsIgnoreCase(str)) {
                server = false;
            } else if ("server".equalsIgnoreCase(str)) {
                server = true;
            } else {
                parseError("could not understand ntmode value '" + str + "'");
            }
        }

        // cameras
        JsonElement camerasElement = obj.get("cameras");
        if (camerasElement == null) {
            parseError("could not read cameras");
            return false;
        }
        JsonArray cameras = camerasElement.getAsJsonArray();
        for (JsonElement camera : cameras) {
            if (!readCameraConfig(camera.getAsJsonObject())) {
                return false;
            }
        }

        if (obj.has("switched cameras")) {
            JsonArray switchedCameras = obj.get("switched cameras").getAsJsonArray();
            for (JsonElement camera : switchedCameras) {
                if (!readSwitchedCameraConfig(camera.getAsJsonObject())) {
                    return false;
                }
            }
        }

        return true;
    }
}