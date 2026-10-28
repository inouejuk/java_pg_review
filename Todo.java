import java.time.LocalDate;

public class Todo {
    private final long id;
    private final String title;
    private boolean done;
    private final LocalDate dueDate;
    private final LocalDate createdDate;

    public Todo(long id, String title) {
        this(id, title, false, null);
    }

    public Todo(long id, String title, boolean done, LocalDate dueDate) {
        this(id, title, done, dueDate, null);
    }

    public Todo(long id, String title, boolean done, LocalDate dueDate, LocalDate createdDate) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.dueDate = dueDate;
        this.createdDate = createdDate;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }
}
