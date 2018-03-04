package lib;

/**
 *
 */
public class AppendEntriesReply {
    public int term;
    public boolean success;
    public int nextIndex;

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
}
