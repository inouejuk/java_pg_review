import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public class App {
    static final String DB_URL = "jdbc:sqlite:todos.db";

    public static void main(String[] args) throws Exception {
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", App::handle);
        server.start();
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）");
    }

    static void initializeDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER, due_date TEXT, created_date TEXT)")) {
                statement.executeUpdate();
            }
            boolean hasDueDate = false;
            boolean hasCreatedDate = false;
            try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(todos)");
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if ("due_date".equals(result.getString("name"))) {
                        hasDueDate = true;
                    }
                    if ("created_date".equals(result.getString("name"))) {
                        hasCreatedDate = true;
                    }
                }
            }
            if (!hasDueDate) {
                try (PreparedStatement statement = connection
                        .prepareStatement("ALTER TABLE todos ADD COLUMN due_date TEXT")) {
                    statement.executeUpdate();
                }
            }
            if (!hasCreatedDate) {
                try (PreparedStatement statement = connection
                        .prepareStatement("ALTER TABLE todos ADD COLUMN created_date TEXT")) {
                    statement.executeUpdate();
                }
            }
        }
    }

    static void handle(HttpExchange exchange) throws IOException {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            if (path.equals("/add") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> parameters = parseParameters(body);
                String title = parameters.getOrDefault("todo", "").strip();
                LocalDate dueDate = parseDueDate(parameters.get("dueDate"));
                LocalDate createdDate = parseDate(parameters.getOrDefault("createdDate", LocalDate.now().toString()));
                if (!title.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO todos (title, done, due_date, created_date) VALUES (?, 0, ?, ?)")) {
                        statement.setString(1, title);
                        statement.setString(2, dueDate == null ? null : dueDate.toString());
                        statement.setString(3, createdDate == null ? null : createdDate.toString());
                        statement.executeUpdate();
                    }
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/update") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> parameters = parseParameters(body);
                Long id = parseId(parameters.get("id"));
                String newTitle = parameters.getOrDefault("title", "").strip();
                LocalDate dueDate = parseDueDate(parameters.get("dueDate"));
                LocalDate createdDate = parseDate(parameters.get("createdDate"));
                if (id != null && !newTitle.isBlank()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE todos SET title = ?, "
                                    + "due_date = CASE WHEN ? THEN ? ELSE due_date END, "
                                    + "created_date = CASE WHEN ? THEN ? ELSE created_date END WHERE id = ?")) {
                        statement.setString(1, newTitle);
                        statement.setBoolean(2, parameters.containsKey("dueDate"));
                        statement.setString(3, dueDate == null ? null : dueDate.toString());
                        statement.setBoolean(4, parameters.containsKey("createdDate"));
                        statement.setString(5, createdDate == null ? null : createdDate.toString());
                        statement.setLong(6, id);
                        statement.executeUpdate();
                    }
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/done") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        long id = Long.parseLong(query.substring(3));
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE todos SET done = NOT done WHERE id = ?")) {
                            statement.setLong(1, id);
                            statement.executeUpdate();
                        }
                    } catch (NumberFormatException e) {
                    }
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/delete") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        long id = Long.parseLong(query.substring(3));
                        try (PreparedStatement statement = connection.prepareStatement(
                                "DELETE FROM todos WHERE id = ?")) {
                            statement.setLong(1, id);
                            statement.executeUpdate();
                        }
                    } catch (NumberFormatException e) {
                    }
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/")) {
                Long editId = parseId(parseParameters(exchange.getRequestURI().getRawQuery()).get("edit"));
                message = renderPage(connection, editId);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            } else {
                message = "ページが見つかりません";
            }
            byte[] body = message.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        } catch (DateTimeParseException e) {
            byte[] body = "登録日・締め切り日は有効な日付（YYYY-MM-DD）で入力してください".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
        } catch (SQLException e) {
            e.printStackTrace();
            byte[] body = "データベース処理に失敗しました".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    static String renderPage(Connection connection, Long editId) throws SQLException {
        StringBuilder html = new StringBuilder(
                """
                        <!DOCTYPE html>
                        <html lang="ja">
                        <head>
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>わたしのTodo</title>
                          <style>
                            :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Meiryo", sans-serif; color: #243448; background: #f3f6fa; }
                            * { box-sizing: border-box; }
                            body { margin: 0; font-size: 16px; line-height: 1.6; }
                            main { max-width: 1060px; margin: 48px auto; padding: 0 24px; }
                            .eyebrow { margin: 0 0 8px; color: #087e76; font-size: 13px; font-weight: 700; letter-spacing: .12em; }
                            h1 { margin: 0; font-size: clamp(26px, 5vw, 36px); letter-spacing: -.03em; }
                            .intro { margin: 8px 0 28px; color: #5b6b7d; }
                            h2 { margin: 0; font-size: 18px; }
                            .panel { padding: 24px; border: 1px solid #dce4ee; border-radius: 18px; background: #fff; box-shadow: 0 8px 28px #26364a08; }
                            .panel h2 { margin-bottom: 18px; }
                            .task-form { display: grid; grid-template-columns: minmax(0, 1fr) 172px 172px auto; gap: 14px; align-items: end; }
                            .field { min-width: 0; }
                            label { display: block; margin-bottom: 7px; font-size: 13px; font-weight: 700; }
                            input { width: 100%; min-width: 0; height: 44px; padding: 9px 11px; border: 1px solid #aebdcd; border-radius: 8px; background: #fff; color: #243448; font: inherit; }
                            input::placeholder { color: #68798c; }
                            button, .button { display: inline-flex; justify-content: center; align-items: center; min-height: 44px; padding: 9px 15px; border: 1px solid #cbd6e2; border-radius: 8px; color: #34475e; background: #fff; font: inherit; font-size: 14px; font-weight: 600; line-height: 1.5; text-decoration: none; cursor: pointer; white-space: nowrap; }
                            button { border-color: #087e76; background: #087e76; color: #fff; }
                            button:hover { border-color: #06675f; background: #06675f; }
                            .button:hover { background: #eef3f8; border-color: #aebdcd; }
                            a:focus-visible, button:focus-visible, input:focus-visible { outline: 3px solid #168bce; outline-offset: 3px; }
                            .list-heading { margin: 30px 0 14px; }
                            .todo-list { display: grid; gap: 14px; padding: 0; margin: 0; list-style: none; }
                            .todo-card { padding: 22px 24px; border: 1px solid #dce4ee; border-radius: 14px; background: #fff; }
                            .todo-top { display: flex; align-items: flex-start; gap: 12px; }
                            .todo-title { flex: 1; min-width: 0; margin: 0; font-size: 17px; font-weight: 600; overflow-wrap: anywhere; white-space: pre-wrap; }
                            .status { flex: none; padding: 3px 10px; border-radius: 20px; background: #eef3f8; color: #52647a; font-size: 12px; font-weight: 700; }
                            .is-done .status { background: #e3f4ed; color: #216346; }
                            .is-done .todo-title { color: #64748b; text-decoration: line-through; }
                            .todo-bottom { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 18px; }
                            .dates { display: flex; flex-wrap: wrap; gap: 12px 28px; margin: 0; }
                            .dates div { display: grid; gap: 2px; }
                            dt { font-size: 12px; color: #5b6b7d; }
                            dd { margin: 0; font-size: 14px; font-variant-numeric: tabular-nums; }
                            .actions { display: flex; flex-wrap: wrap; gap: 8px; }
                            .danger { color: #b33240; border-color: #e7c9ce; }
                            .danger:hover { background: #fff1f2; border-color: #d6a5ad; }
                            .edit-form { grid-template-columns: minmax(0, 1fr) 172px 172px; }
                            .edit-form .actions { grid-column: 1 / -1; }
                            .empty-state { padding: 44px 20px; border: 1px dashed #bdcbd9; border-radius: 14px; background: #fff; text-align: center; }
                            .empty-state p { margin: 6px 0 0; color: #5b6b7d; font-size: 14px; }
                            @media (max-width: 800px) {
                              .task-form, .edit-form { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                              .title-field, .task-form > button { grid-column: 1 / -1; }
                              .todo-bottom { align-items: flex-start; flex-direction: column; }
                            }
                            @media (max-width: 480px) {
                              main { margin: 28px auto; padding: 0 16px; }
                              .panel, .todo-card { padding: 18px; }
                              .task-form, .edit-form { grid-template-columns: minmax(0, 1fr); }
                            }
                          </style>
                        </head>
                        <body>
                          <main>
                            <header>
                              <p class="eyebrow">MY DAILY TASKS</p>
                              <h1>わたしのTodo</h1>
                              <p class="intro">やることと日付を整理して、ひとつずつ進めましょう。</p>
                            </header>
                            <section class="panel" aria-labelledby="add-heading">
                              <h2 id="add-heading">新しいTodo</h2>
                              <form class="task-form" method="post" action="/add">
                                <div class="field title-field">
                                  <label for="todo-title">やること</label>
                                  <input id="todo-title" name="todo" placeholder="例：打ち合わせの資料を作る" required>
                                </div>
                        """);
        html.append(dateField("created-date", "createdDate", "登録日", LocalDate.now()));
        html.append(dateField("due-date", "dueDate", "締め切り日", null));
        html.append("""
                        <button type="submit">追加する</button>
                      </form>
                    </section>
                    <section aria-labelledby="list-heading">
                      <h2 class="list-heading" id="list-heading">Todo一覧</h2>
                """);
        StringBuilder items = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title, done, due_date, created_date FROM todos ORDER BY id");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Todo todo = new Todo(result.getLong("id"), result.getString("title"),
                        result.getInt("done") == 1, parseDueDate(result.getString("due_date")),
                        parseDate(result.getString("created_date")));
                items.append(renderTodo(todo, editId));
            }
        }
        if (items.length() == 0) {
            html.append("<div class='empty-state'><strong>やることは、いまゼロです</strong>"
                    + "<p>上のフォームから、新しいTodoを追加できます。</p></div>");
        } else {
            html.append("<ul class='todo-list'>").append(items).append("</ul>");
        }
        return html.append("</section></main></body></html>").toString();
    }

    static String renderTodo(Todo todo, Long editId) {
        long id = todo.getId();
        String title = escapeHtml(todo.getTitle());
        StringBuilder html = new StringBuilder("<li class='todo-card");
        html.append(todo.isDone() ? " is-done'>" : "'>");
        if (editId != null && editId.longValue() == id) {
            html.append("<form class='task-form edit-form' method='post' action='/update'>")
                    .append("<input type='hidden' name='id' value='").append(id).append("'>")
                    .append("<div class='field title-field'><label for='edit-title-").append(id)
                    .append("'>やること</label><input id='edit-title-").append(id)
                    .append("' type='text' name='title' value='").append(title)
                    .append("' required autofocus></div>")
                    .append(dateField("edit-created-date-" + id, "createdDate", "登録日", todo.getCreatedDate()))
                    .append(dateField("edit-due-date-" + id, "dueDate", "締め切り日", todo.getDueDate()))
                    .append("<div class='actions'><button type='submit'>保存する</button>")
                    .append("<a class='button' href='/'>キャンセル</a></div></form>");
        } else {
            html.append("<div class='todo-top'><h3 class='todo-title'>").append(title)
                    .append("</h3><span class='status'>").append(todo.isDone() ? "完了" : "未完了")
                    .append("</span></div><div class='todo-bottom'><dl class='dates'>")
                    .append(dateDisplay("登録日", todo.getCreatedDate()))
                    .append(dateDisplay("締め切り日", todo.getDueDate()))
                    .append("</dl><div class='actions'><a class='button' href='/done?id=").append(id)
                    .append("'>").append(todo.isDone() ? "未完了に戻す" : "完了にする")
                    .append("</a><a class='button' href='/?edit=").append(id).append("'>編集</a>")
                    .append("<a class='button danger' href='/delete?id=").append(id)
                    .append("'>削除</a></div></div>");
        }
        return html.append("</li>").toString();
    }

    static String dateField(String id, String name, String label, LocalDate value) {
        return "<div class='field'><label for='" + id + "'>" + label + "</label>"
                + "<input type='date' id='" + id + "' name='" + name + "' value='"
                + (value == null ? "" : value.toString()) + "'></div>";
    }

    static String dateDisplay(String label, LocalDate value) {
        String date = value == null ? "未設定" : "<time datetime='" + value + "'>" + value + "</time>";
        return "<div><dt>" + label + "</dt><dd>" + date + "</dd></div>";
    }

    static Map<String, String> parseParameters(String encoded) {
        Map<String, String> parameters = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return parameters;
        }
        try {
            for (String parameter : encoded.split("&")) {
                String[] pair = parameter.split("=", 2);
                String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                parameters.put(name, value);
            }
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
        return parameters;
    }

    static Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate parseDueDate(String value) {
        return parseDate(value);
    }

    static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.strip());
    }

    static String escapeHtml(String value) {
        return value == null ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;")
                        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
