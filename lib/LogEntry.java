package lib;

public class LogEntry {
    public int index;
    public int term;
    public Integer command;

    public LogEntry(int index, int term, Integer command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }
}
