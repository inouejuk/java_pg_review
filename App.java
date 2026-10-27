import com.sun.net.httpserver.HttpServer; // Javaに付属するWebサーバー（ブラウザに応答する仕組み）を使う
import java.net.InetSocketAddress; // 接続を待ち受ける番号を指定するための部品を使う

public class App { // Appという名前でプログラム全体をまとめる
    public static void main(String[] args) throws Exception { // 起動時の処理を書き、エラーが起きたらそのまま知らせる
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番のポート（接続の窓口）で待ち受けるサーバーを用意する
        server.createContext("/", exchange -> { // 「/」へのアクセスを受けたときの処理をここに直接書く
            String path = exchange.getRequestURI().getPath(); // アクセスされたパス（URLの「/hello」などの部分）を取り出す
            String message; // 返す文字を入れる変数（名前付きの入れ物）を先に用意する
            if (path.equals("/hello")) { // パスが「/hello」と同じか比べる
                String query = exchange.getRequestURI().getRawQuery(); // クエリ（URLの「?」より後ろの部分）を取り出す
                String name = query.substring(5); // 先頭の「name=」の5文字を除いた残りを切り出す
                message = "こんにちは、" + name + "さん！"; // 「+」で名前とあいさつをつなぐ
            } else if (path.equals("/bye")) { // それ以外なら、パスが「/bye」と同じか比べる
                message = "さようなら！";
            } else {
                message = "ページが見つかりません";
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 普通の文字として、UTF-8（日本語も扱える文字の表し方）で返すと伝える
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のバイト列（通信で送れる数値の並び）に変える
            exchange.sendResponseHeaders(200, body.length); // 200（正常に処理できた印）と送るバイト数を伝える
            exchange.getResponseBody().write(body); // 用意した文字のデータをブラウザに送る
            exchange.getResponseBody().close(); // 文字のデータを送るための出口を閉じる
        });
        server.start(); // サーバーを起動してアクセスを待ち始める
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動したことと止め方をターミナル（コマンドを入力する画面）に表示する
    }
}
