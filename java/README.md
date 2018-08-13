## サーバでは実行しないでください！！！！
## データベースの準備(開発用)

アプリケーションを起動する前にMySQLを起動してください。

以下のコマンドでデータベースのスキーマを生成してください。

    $ cd ../sql
    $ echo "drop database hidakkathon; create database hidakkathon" | mysql -u root hidakkathon; mysql -u root hidakkathon < ../sql/schema.sql;


## アプリケーションの実行方法(ローカル開発用)

MySQLが起動した状態で

    $ ./mvnw spring-boot:run

を実行すればアプリケーションが起動します(初回の`mvnw`実行時には10分ほど時間がかかります)。[http://localhost:8080](http://localhost:8080)にアクセスしてください。


MySQLの接続情報を変える場合は`src/main/resources/application.properties`を編集するか、以下の環境変数を設定してください。

* `HIDAKKATHON_DB_HOST`
* `HIDAKKATHON_DB_PORT`
* `HIDAKKATHON_DB_USER`
* `HIDAKKATHON_DB_PASSWORD`
* `HIDAKKATHON_DB_NAME`


## IDEにインポート(ローカル開発用)

インポートする前に必ず以下を実行してください。静的ファイルを`../static`からクラスパス(`target`)にコピーするためです。

    $ ./mvnw compile

`./mvnw spring-boot:run`を実行済みの場合は、上記コマンドを実行する必要はありません。

開発中は`hitme.App`クラスの`main`メソッドを実行すれば良いです。

## 実行可能jarの作り方

    $ ./mvnw clean package

作成したjarファイルは以下のコマンドで実行可能です。

    $ java -jar target/jiriqi1-0.0.1-SNAPSHOT.jar

