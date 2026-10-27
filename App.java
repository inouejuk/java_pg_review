import com.sun.net.httpserver.HttpServer; // Javaに付属するWebサーバー（ブラウザに応答する仕組み）を使う
import java.net.InetSocketAddress; // 接続を待ち受ける番号を指定するための部品を使う
import java.net.URLDecoder; // フォームの送信で変換された日本語などを元に戻す部品を使う
import java.nio.charset.StandardCharsets; // UTF-8（日本語も扱える文字の表し方）を指定する部品を使う
import java.util.ArrayList; // 要素を追加できる一覧を作る部品を使う
import java.util.List; // List（データを順番にためる入れ物）を使う

public class App { // Appという名前でプログラム全体をまとめる
    static List<Todo> todos = new ArrayList<>(); // ★変更 Todo（番号・やること・完了状態をまとめたデータ）をためる一覧を、起動処理の外に置く
    static int nextId = 1; // ★変更 次のTodoに付ける番号を1から始める

    public static void main(String[] args) throws Exception { // 起動時の処理を書き、エラーが起きたらそのまま知らせる
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番のポート（接続の窓口）で待ち受けるサーバーを用意する
        todos.add(new Todo(nextId++, "牛乳を買う")); // ★変更 1番のサンプルを作って追加し、次の番号を1増やす
        Todo egg = new Todo(nextId++, "卵を買う"); // ★変更 2番のサンプルをeggという変数（名前付きの入れ物）に入れ、次の番号を1増やす
        egg.setDone(true); // ★変更 卵を買うTodoを完了にする
        todos.add(egg); // ★変更 完了にした卵のTodoを一覧に追加する
        server.createContext("/", exchange -> { // 「/」へのアクセスを受けたときの処理をここに直接書く
            String path = exchange.getRequestURI().getPath(); // ★変更 アクセスされたパス（URLの「/add」などの部分）を取り出す
            String method = exchange.getRequestMethod(); // 送信方法（GETやPOST）を取り出す
            String message; // 返す文字を入れる変数（名前付きの入れ物）を先に用意する
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 普通の文字として、UTF-8（日本語も扱える文字の表し方）で返すと伝える
            if (path.equals("/add") && method.equals("POST")) { // 「/add」へのPOST（フォームの内容を送る方法）なら追加する
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 送られた中身をUTF-8の文字として受け取る
                String value = body.substring(5); // 先頭の「todo=」の5文字を除いた残りを切り出す
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8); // ★変更 送信用に変換された文字を元に戻し、title（やること）に入れる
                if (!title.isEmpty()) { // ★変更 入力が空でなければ追加する
                    todos.add(new Todo(nextId, title)); // ★変更 次の番号と入力したやることでTodoを作り、一覧に追加する
                    nextId++; // ★変更 次に付ける番号を1増やす
                } // 空の入力かどうかの分岐はここまで
                exchange.getResponseHeaders().set("Location", "/"); // ブラウザの移動先を「/」にする
                exchange.sendResponseHeaders(303, -1); // 303（別の場所へ移動する合図）を本文なしで返す
                exchange.close(); // 今回のやり取りを閉じる
                return; // 今回のアクセスの処理をここで終える
            } else if (path.equals("/")) { // 「/」なら入力フォームとTodoの一覧を表示する
                String html = "<form method='post' action='/add'><input name='todo'><button>追加</button></form><ul>"; // ★変更 入力フォームと一覧の始まりをhtml（画面の内容）に入れる
                for (Todo todo : todos) { // ★変更 一覧からTodoを1件ずつ取り出す
                    String mark = ""; // ★変更 完了の印は、最初は空の文字にする
                    if (todo.isDone()) { // ★変更 このTodoが完了していれば、印を付ける
                        mark = " ✔"; // ★変更 空白と完了の印を入れる
                    } // ★変更 完了しているかどうかの分岐はここまで
                    html += "<li>" + todo.getTitle() + mark + "</li>"; // ★変更 やることと印をHTML（画面の構造を書く形式）の一覧項目として足す
                } // 一覧の繰り返しはここまで
                html += "</ul>"; // ★変更 一覧を閉じる
                message = html; // ★変更 作った画面の内容を、ブラウザへ返す文字にする
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // この応答はUTF-8のHTMLとして表示すると伝える
            } else { // ★変更 使わない分岐を削除し、どちらにも当てはまらないパスをここで受ける
                message = "ページが見つかりません";
            }
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のバイト列（通信で送れる数値の並び）に変える
            exchange.sendResponseHeaders(200, body.length); // 200（正常に処理できた印）と送るバイト数を伝える
            exchange.getResponseBody().write(body); // 用意した文字のデータをブラウザに送る
            exchange.getResponseBody().close(); // 文字のデータを送るための出口を閉じる
        });
        server.start(); // サーバーを起動してアクセスを待ち始める
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動したことと止め方をターミナル（コマンドを入力する画面）に表示する
    }
}

class Todo { // ★変更 Todo（1件のやることをまとめる設計図）を作る
    private final int id; // ★変更 番号を保存する。privateはこのクラス内だけ、finalは後から変更できないという意味
    private final String title; // ★変更 やることの文字を保存し、後から変更できないようにする
    private boolean done; // ★変更 完了したかを保存する。booleanはtrue（はい）かfalse（いいえ）を表す

    Todo(int id, String title) { // ★変更 コンストラクタ（newで作るときに値を受け取る部分）。publicは付けない
        this.id = id; // ★変更 受け取った番号を、このTodoのidに保存する
        this.title = title; // ★変更 受け取ったやることを、このTodoのtitleに保存する
        this.done = false; // ★変更 作った直後は未完了にする
    } // ★変更 作るときの処理はここまで

    int getId() { // ★変更 番号を読み出すメソッド（名前を付けた処理）
        return id; // ★変更 保存してある番号を返す
    } // ★変更 getIdの処理はここまで

    String getTitle() { // ★変更 やることを読み出すメソッド（名前を付けた処理）
        return title; // ★変更 保存してあるやることを返す
    } // ★変更 getTitleの処理はここまで

    boolean isDone() { // ★変更 完了したかを読み出すメソッド（名前を付けた処理）
        return done; // ★変更 保存してある完了状態を返す
    } // ★変更 isDoneの処理はここまで

    void setDone(boolean done) { // ★変更 完了状態を書き換えるメソッド（名前を付けた処理）
        this.done = done; // ★変更 受け取った完了状態を、このTodoのdoneに保存する
    } // ★変更 setDoneの処理はここまで
} // ★変更 Todoクラスはここまで
