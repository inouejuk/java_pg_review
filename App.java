import com.sun.net.httpserver.HttpServer; // Javaに付属するWebサーバー（ブラウザに応答する仕組み）を使う
import java.net.InetSocketAddress; // 接続を待ち受ける番号を指定するための部品を使う
import java.net.URLDecoder; // フォームの送信で変換された日本語などを元に戻す部品を使う
import java.nio.charset.StandardCharsets; // UTF-8（日本語も扱える文字の表し方）を指定する部品を使う
import java.util.ArrayList; // 要素を追加できる一覧を作る部品を使う
import java.util.List; // List（データを順番にためる入れ物）を使う

public class App { // Appという名前でプログラム全体をまとめる
    public static void main(String[] args) throws Exception { // 起動時の処理を書き、エラーが起きたらそのまま知らせる
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番のポート（接続の窓口）で待ち受けるサーバーを用意する
        List<String> todos = new ArrayList<>(); // Todoをためる空の一覧を、アクセスを処理する部分の外で一度だけ作る
        server.createContext("/", exchange -> { // 「/」へのアクセスを受けたときの処理をここに直接書く
            String path = exchange.getRequestURI().getPath(); // アクセスされたパス（URLの「/hello」などの部分）を取り出す
            String method = exchange.getRequestMethod(); // 送信方法（GETやPOST）を取り出す
            String message; // 返す文字を入れる変数（名前付きの入れ物）を先に用意する
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 普通の文字として、UTF-8（日本語も扱える文字の表し方）で返すと伝える
            if (path.equals("/add") && method.equals("POST")) { // 「/add」へのPOST（フォームの内容を送る方法）なら追加する
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 送られた中身をUTF-8の文字として受け取る
                String value = body.substring(5); // 先頭の「todo=」の5文字を除いた残りを切り出す
                String todo = URLDecoder.decode(value, StandardCharsets.UTF_8); // 送信用に変換された日本語などを元に戻す
                if (!todo.isEmpty()) { // 入力が空でなければ追加する
                    todos.add(todo); // Todoを一覧の末尾に追加する
                } // 空の入力かどうかの分岐はここまで
                exchange.getResponseHeaders().set("Location", "/"); // ブラウザの移動先を「/」にする
                exchange.sendResponseHeaders(303, -1); // 303（別の場所へ移動する合図）を本文なしで返す
                exchange.close(); // 今回のやり取りを閉じる
                return; // 今回のアクセスの処理をここで終える
            } else if (path.equals("/")) { // 「/」なら入力フォームとTodoの一覧を表示する
                message = "<form method='post' action='/add'><input name='todo'><button>追加</button></form><ul>"; // 入力フォームと一覧の始まりを用意する
                for (String todo : todos) { // 一覧のTodoを一件ずつ取り出す
                    message += "<li>" + todo + "</li>"; // TodoをHTML（画面の構造を書く形式）の一覧項目としてつなぐ
                } // 一覧の繰り返しはここまで
                message += "</ul>"; // 一覧を閉じる
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // この応答はUTF-8のHTMLとして表示すると伝える
            } else if (path.equals("/hello")) { // パスが「/hello」と同じか比べる
                String query = exchange.getRequestURI().getRawQuery(); // クエリ（URLの「?」より後ろの部分）を取り出す
                String name = query.substring(5); // 先頭の「name=」の5文字を除いた残りを切り出す
                message = "こんにちは、" + name + "さん！"; // 「+」で名前とあいさつをつなぐ
            } else if (path.equals("/bye")) { // それ以外なら、パスが「/bye」と同じか比べる
                message = "さようなら！";
            } else {
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
