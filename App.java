import com.sun.net.httpserver.HttpExchange; // ★
import com.sun.net.httpserver.HttpServer;
import java.io.IOException; // ★ HTTP処理で発生する例外を扱う。
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection; // ★
import java.sql.DriverManager; // ★
import java.sql.PreparedStatement; // ★
import java.sql.ResultSet; // ★
import java.sql.SQLException; // ★

public class App {
    static final String DB_URL = "jdbc:sqlite:todos.db"; // ★

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★
                PreparedStatement statement = connection.prepareStatement( // ★
                        "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)")) { // ★
            statement.executeUpdate(); // ★
        } // ★
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", App::handle); // ★
        server.start(); // ★
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // ★
    } // ★

    static void handle(HttpExchange exchange) throws IOException { // ★
        try (Connection connection = DriverManager.getConnection(DB_URL)) { // ★ 接続はリクエストごとに開き、自動で閉じる。
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            if (path.equals("/add") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String value = body.startsWith("todo=") ? body.substring(5) : ""; // ★ 空のリクエストにも対応する。
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8);
                if (!title.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement( // ★
                            "INSERT INTO todos (title, done) VALUES (?, 0)")) { // ★ IDはSQLiteに採番させる。
                        statement.setString(1, title); // ★
                        statement.executeUpdate(); // ★
                    } // ★
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/done") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        long id = Long.parseLong(query.substring(3)); // ★
                        try (PreparedStatement statement = connection.prepareStatement( // ★
                                "UPDATE todos SET done = 1 WHERE id = ?")) { // ★
                            statement.setLong(1, id); // ★
                            statement.executeUpdate(); // ★
                        } // ★
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
                        long id = Long.parseLong(query.substring(3)); // ★
                        try (PreparedStatement statement = connection.prepareStatement( // ★
                                "DELETE FROM todos WHERE id = ?")) { // ★
                            statement.setLong(1, id); // ★
                            statement.executeUpdate(); // ★
                        } // ★
                    } catch (NumberFormatException e) {
                    }
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/")) {
                String html = "<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'>"
                        + "<title>わたしのTodo</title>"
                        + "<style>"
                        + "body { max-width: 600px; margin: 32px auto; padding: 0 16px; font-size: 16px; }"
                        + "h1 { font-size: 24px; margin: 0 0 16px; }"
                        + "form { margin-bottom: 16px; }"
                        + "</style></head><body><h1>わたしのTodo</h1>"
                        + "<form method='post' action='/add'><input name='todo'><button>追加</button></form>";
                html += "<ul>";
                boolean hasTodos = false; // ★
                try (PreparedStatement statement = connection.prepareStatement( // ★
                        "SELECT id, title, done FROM todos ORDER BY id"); // ★
                        ResultSet result = statement.executeQuery()) { // ★
                    while (result.next()) { // ★
                        hasTodos = true; // ★
                        long id = result.getLong("id"); // ★
                        String title = escapeHtml(result.getString("title")); // ★
                        String mark = result.getInt("done") == 1 ? " ✔" : ""; // ★
                        html += "<li>" + title + mark + " <a href='/done?id=" + id // ★
                                + "'>完了</a> <a href='/delete?id=" + id + "'>削除</a></li>"; // ★
                    } // ★
                } // ★
                html += "</ul>"; // ★
                if (!hasTodos) { // ★
                    html += "<p>やることは、いまゼロです</p>"; // ★
                } // ★
                html += "</body></html>"; // ★
                message = html;
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            } else {
                message = "ページが見つかりません";
            }
            byte[] body = message.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        } catch (SQLException e) { // ★
            e.printStackTrace(); // ★
            byte[] body = "データベース処理に失敗しました".getBytes(StandardCharsets.UTF_8); // ★
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // ★
            exchange.sendResponseHeaders(500, body.length); // ★
            exchange.getResponseBody().write(body); // ★
        } finally { // ★
            exchange.close(); // ★
        } // ★
    } // ★

    static String escapeHtml(String value) { // ★ タイトルをHTMLとして解釈させない。
        return value == null ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;") // ★
                        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); // ★
    } // ★
}
