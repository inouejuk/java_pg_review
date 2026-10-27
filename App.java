import com.sun.net.httpserver.HttpServer;
import java.io.IOException; // ファイルの読み書きで発生する例外を扱う。
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files; // 標準APIでファイルを読み書きする。
import java.nio.file.Path; // 保存先のファイルパスを扱う。
import java.util.ArrayList;
import java.util.List;

public class App {
    static List<Todo> todos = new ArrayList<>();
    static int nextId = 1;

    static void save() throws IOException { // Todo全件をファイルへ保存する。
        List<String> lines = new ArrayList<>(); // 書き出す行を用意する。
        for (Todo todo : todos) { // Todo全件を順番に処理する。
            lines.add(todo.getId() + "," + (todo.isDone() ? "1" : "0") + "," + todo.getTitle()); // ID・完了状態・タイトルを1行にまとめる。
        } // 行の作成を終える。
        Files.write(Path.of("todos.csv"), lines, StandardCharsets.UTF_8); // UTF-8で全件を上書きし、0件なら空にする。
    } // 保存処理を終える。

    static void load() throws IOException { // 保存済みのTodoを読み込む。
        todos.clear(); // 復元前にListを空にする。
        nextId = 1; // 保存データが空なら次のIDを1にする。
        Path path = Path.of("todos.csv"); // 読み込むファイルを指定する。
        if (!Files.exists(path)) { // 保存ファイルがなければ空のままにする。
            return; // 読み込みを終える。
        } // 存在確認を終える。
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) { // UTF-8で読み込んだ各行を処理する。
            String[] parts = line.split(",", 3); // 先頭の2つのカンマで分け、タイトル内のカンマを残す。
            int id = Integer.parseInt(parts[0]); // IDを数値に戻す。
            Todo todo = new Todo(id, parts[2]); // 保存されたIDとタイトルでTodoを作る。
            todo.setDone(parts[1].equals("1")); // 1なら完了、0なら未完了に戻す。
            todos.add(todo); // 復元したTodoをListへ戻す。
            nextId = Math.max(nextId, id + 1); // 最大IDの次を新規追加用のIDにする。
        } // 全件の復元を終える。
    } // 読み込み処理を終える。

    public static void main(String[] args) throws Exception {
        load(); // サーバーを作る前に保存済みのTodoを復元する。
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            if (path.equals("/add") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String value = body.substring(5);
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8);
                if (!title.isEmpty()) {
                    todos.add(new Todo(nextId, title));
                    nextId++;
                    save(); // 追加後の全件を保存する。
                }
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            } else if (path.equals("/done") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        int id = Integer.parseInt(query.substring(3));
                        for (Todo todo : todos) {
                            if (todo.getId() == id) {
                                todo.setDone(true);
                                save(); // 完了状態を変更した全件を保存する。
                                break;
                            }
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
                        int id = Integer.parseInt(query.substring(3));
                        if (todos.removeIf(todo -> todo.getId() == id)) { // 対象のTodoが削除されたか確認する。
                            save(); // 削除後の全件を保存する。
                        } // 削除時の保存を終える。
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
                if (todos.isEmpty()) {
                    html += "<p>やることは、いまゼロです</p>";
                }
                html += "<ul>";
                for (Todo todo : todos) {
                    String mark = "";
                    if (todo.isDone()) {
                        mark = " ✔";
                    }
                    html += "<li>" + todo.getTitle() + mark + " <a href='/done?id=" + todo.getId() + "'>完了</a> <a href='/delete?id=" + todo.getId() + "'>削除</a></li>";
                }
                html += "</ul></body></html>";
                message = html;
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            } else {
                message = "ページが見つかりません";
            }
            byte[] body = message.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）");
    }
}

class Todo {
    private final int id;
    private final String title;
    private boolean done;

    Todo(int id, String title) {
        this.id = id;
        this.title = title;
        this.done = false;
    }

    int getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    boolean isDone() {
        return done;
    }

    void setDone(boolean done) {
        this.done = done;
    }
}
