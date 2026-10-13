package vcnity.pipeline;

import java.util.List;

public record PipelineResult(List<Outcome> outcomes) {

    public List<Outcome> clean() {
        return outcomes.stream().filter(o -> o.stageReached() == Outcome.Stage.CLEAN).toList();
    }

    public List<Outcome> exceptions() {
        return outcomes.stream().filter(o -> o.stageReached() == Outcome.Stage.EXCEPTIONS_QUEUE).toList();
    }

    public List<Outcome> rejected() {
        return outcomes.stream().filter(o -> o.stageReached() == Outcome.Stage.REJECTED_AT_GATE).toList();
    }
}
