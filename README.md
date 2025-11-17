# Player Inventory Viewer

MinecraftサーバーのプレイヤーインベントリをローカルホストのWebページで確認できるプラグインです。

## 機能

- 🎒 **インベントリ表示**: プレイヤーのメインインベントリとホットバーを表示
- 🛡️ **防具表示**: 装備中の防具を確認
- 📊 **プレイヤーステータス**: 体力、満腹度、レベル、経験値を表示
- 🌐 **Web インターフェース**: ローカルホスト（http://localhost:8080）で閲覧
- 🔄 **リアルタイム更新**: 30秒ごとに自動更新
- 📱 **レスポンシブデザイン**: PC・タブレット・スマートフォン対応

## 必要環境

- **Minecraft Server**: Spigot/Paper 1.20.1以上
- **Java**: 17以上
- **ビルドツール**: Maven または Gradle

## インストール方法

### 1. プラグインをビルド

#### Mavenを使用する場合:
```bash
mvn clean package
```

#### Gradleを使用する場合:
```bash
./gradlew shadowJar
```

### 2. プラグインをサーバーに設置

1. `target/PlayerInventoryViewer-1.0.0.jar` (Maven) または `build/libs/PlayerInventoryViewer-1.0.0-all.jar` (Gradle) をサーバーの `plugins` フォルダにコピー
2. サーバーを再起動

### 3. Webページにアクセス

ブラウザで http://localhost:8080 にアクセス

## 使用方法

1. **プレイヤー選択**: 
   - オンライン/オフラインプレイヤーの一覧から確認したいプレイヤーをクリック
   - 検索ボックスでプレイヤー名を検索可能

2. **インベントリ確認**:
   - 選択されたプレイヤーのインベントリ、防具、ステータスが表示されます
   - オンラインプレイヤーのみリアルタイムデータが表示されます

3. **自動更新**:
   - 30秒ごとにデータが自動更新されます
   - 手動で「🔄 更新」ボタンをクリックして更新することも可能

## 設定

`config.yml` でWebサーバーの設定を変更できます:

```yaml
# Webサーバー設定
web:
  port: 8080
  host: "localhost"

# データ更新間隔（秒）
update-interval: 30

# ログ設定
debug: false
```

## 権限

```yaml
permissions:
  playerinventoryviewer.view:
    description: インベントリを確認する権限
    default: op
  playerinventoryviewer.admin:
    description: 管理者権限
    default: op
```

## API エンドポイント

プラグインは以下のAPIエンドポイントを提供します:

- `GET /api/players` - プレイヤー一覧取得
- `GET /api/inventory?player=<プレイヤー名>` - 指定プレイヤーのインベントリ取得

## トラブルシューティング

### Webページにアクセスできない場合

1. サーバーのポート8080が他のアプリケーションで使用されていないか確認
2. ファイアウォールでポート8080がブロックされていないか確認
3. `config.yml` でポート設定を変更して再起動

### プレイヤーデータが表示されない場合

1. プラグインが正常に読み込まれているか確認
2. オフラインプレイヤーのデータは表示されません（仕様）
3. サーバーログでエラーメッセージを確認

## 開発者向け情報

### プロジェクト構造

```
PlayerInventoryViewer/
├── src/main/java/com/example/playerinventoryviewer/
│   └── PlayerInventoryViewer.java
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   └── web/
│       ├── index.html
│       └── static/
│           ├── css/style.css
│           └── js/script.js
├── pom.xml
├── build.gradle
└── README.md
```

### カスタマイズ

- **スタイル変更**: `web/static/css/style.css` を編集
- **機能追加**: `web/static/js/script.js` と `PlayerInventoryViewer.java` を編集
- **ポート変更**: `config.yml` の `web.port` を変更

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。

## サポート

問題が発生した場合は、以下を確認してください:

1. サーバーログのエラーメッセージ
2. ブラウザの開発者ツールでのJavaScriptエラー
3. プラグインの権限設定
4. Javaのバージョン（17以上が必要）

---

**注意**: このプラグインはローカルネットワーク内での使用を想定しています。インターネット上に公開する場合は、適切なセキュリティ対策を実施してください。