/**
 * Interpret the result of the ball pipeline.  This abstracts out the logic
 * from the pipeline class.
 *
 * @author Chuck Benedict, Mentor, Team 997
 */
public class GripPipelineInterpreter {

    // Processed pipeline that we will do the interpretation against
    private GripPipeline pipeline;

    /**
     * Constructor taking a processed pipeline
     *
     * @param pipeline	A processed pipeline that returns blob found results
     */
    public GripPipelineInterpreter(GripPipeline pipeline) {
        if (pipeline == null)
        {
            throw new IllegalArgumentException("Pipline cannot be null.");
        }
        this.pipeline = pipeline;
    }

    /**
     * Get the count of the number of balls found on a processed frame.
     *
     * @return The count of the number of balls found
     */
    public long countourCount() {
        return this.pipeline.findContoursOutput().size();
    }
}