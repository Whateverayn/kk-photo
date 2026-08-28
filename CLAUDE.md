# プロジェクト: kk Photo (Android)

## 概要
Androidの写真(将来的には動画も)を対象にした、EXIF/XMP保持リサイズアプリ。
個人利用オンリー、配布・Play Store公開の予定なし。

## 技術方針
- Kotlin + Jetpack Compose。React Native/Flutterのような非ネイティブフレームワークは使わない
- Build configuration language: Kotlin DSL (build.gradle.kts)
- minSdk 30 (Android 11) / targetSdk 最新(必要なら実際のプロジェクト設定に合わせて調整して)
- 検証機はPixel 8 Pro(Android 17)1台のみ、ワイヤレスデバッグ接続。エミュレータは使わない(ストレージ節約のため)

## アプリ名
- プロダクト名として呼ぶ時: kk Photo
- 表示ラベル(app_name文字列リソース): kk Photos(複数形)

## コア機能要件
1. **期間指定一括処理**: MediaStoreをDATE_TAKEN(nullならDATE_ADDEDにフォールバック)で範囲クエリ
2. **リサイズ**: 目標0.3Mpx(VGA程度)、アスペクト比維持、inSampleSizeで粗デコード→Bitmap.createScaledBitmapで最終サイズ (サイズは選択肢と自由設定 (ピクセル数 (面積) の方式と，縦または横または長辺の大きさ方式の両方．リサイズ方式は仮なので，要検討．できるだけ品質を落とさないようにしたい．)
3. **EXIF/XMP保持**: androidx.exifinterface(1.4.x系、TAG_XMP対応済み)で元画像の全タグを出力ファイルにコピー。Orientationはビットマップ回転済みなら1にリセット
4. **出力フォーマット**: まずはJPEGのみ。WebPは将来検討候補(exifinterfaceがEXIF/XMP書き込み対応済み)。HEIC/AVIFはメタデータ書き込みがまだ未成熟なので要検討
5. **重複スキップ**: Roomで管理。キーはMediaStoreの_ID + size + date_modified (要検討．ハッシュとかでもいいかも？)
6. **保存先**: 自アプリ専用領域(getExternalFilesDir)に保存。MediaStoreに乗らないのでGalleryの写真一覧には出ない(意図的な要件)。ファイラからは辿れてOK
7. **場所を選んで書き出したい場合**: SAFの ACTION_CREATE_DOCUMENT
8. **共有シート**: 送る側(ACTION_SEND/SEND_MULTIPLE + FileProvider)、受け取る側(intent-filterでACTION_SEND登録)の両方に対応

## スコープ外(今回はやらない)
- 動画処理は将来検討。
- 撮影日時が無い写真への日時書き込み機能(ファイル名/ファイル日時から半自動推定)は将来追加候補、今回は未着手

## 開発ワークフロー
- コード編集はこちら(Claude.appのCode)で行う
- Android Studio本体はGUI操作専用(Layout Inspector、Profiler、Compose Preview、Vector Asset Studioなど、テキストファイル編集で完結しない作業)
- lint/ビルド確認は都度 `./gradlew lintDebug` や `./gradlew build` を実行して結果を見る(IDEのようにリアルタイムでは拾えないため)
- 実装は小さな塊に分けて進める。1ターンで一気に大きく変更せず、動作確認できる単位ごとにユーザーへ turn を返す

## 最初の実装単位
「MediaStoreクエリ→リサイズ→自領域保存」の最小ループから着手。共有シートとRoom重複管理は後乗せでOK。

## お願い

* 基本的に独自のレイアウトや配色等は避けて，原則として標準のものを使う．
* Android studio側でGUI操作をした方がいいことは遠慮せずにお申し付けください．(下手にテキストファイルを編集して壊れる方が良くないので．

## 進捗ログ
- [x] 権限リクエスト(READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE)の最小実装
  - `AndroidManifest.xml` に権限宣言追加
  - `MainActivity.kt` に権限リクエストボタン + 許可状態表示のCompose UI追加
  - `app_name` を仕様通り "kk Photos" に修正
- [ ] MediaStoreクエリ(期間指定、DATE_TAKEN/DATE_ADDED)
- [ ] リサイズ処理
- [ ] EXIF/XMP保持コピー
- [ ] 自領域保存
- [ ] 重複スキップ(Room)
- [ ] SAF書き出し
- [ ] 共有シート(送信/受信)
