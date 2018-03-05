package lib;

public class LogEntry {
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public Integer getCommand() {
        return command;
    }

    public void setCommand(Integer command) {
        this.command = command;
    }

    private int index;
    private int term;
    private Integer command;

    public LogEntry(int index, int term, Integer command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }
}
