import edu.wpi.first.vision.VisionPipeline;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;

import java.util.ArrayList;

public class PipelineWrapper implements VisionPipeline {
    private GripPipeline gripPipeline;
    private Mat sourceImage;

    public PipelineWrapper(){
        gripPipeline= new GripPipeline();
    }

    public VisionPipeline getPipeline(){
        return gripPipeline;
    }

    public Mat getSourceImage(){
        return sourceImage;
    }

    public void process(Mat mat) {
        sourceImage = mat.clone();
        gripPipeline.process(mat);
    }

    public ArrayList<MatOfPoint> findContoursOutput(){
        return gripPipeline.findContoursOutput();
    }
}