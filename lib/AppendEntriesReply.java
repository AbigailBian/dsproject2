package lib;

import java.io.Serializable;

/**
 *
 */
public class AppendEntriesReply implements Serializable{

    private int term;
    private boolean success;
    private int nextIndex;

    /**
     *
     * @param term
     * @param success
     * @param nextIndex
     */
    public AppendEntriesReply(int term, boolean success, int nextIndex) {
        this.term = term;
        this.success = success;
        this.nextIndex = nextIndex;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }
}
