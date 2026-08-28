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

## 開発メモ
- GitHubリポジトリ: https://github.com/Whateverayn/kk-photo (private)。リモートはHTTPS(gh credential helper経由。SSH鍵は未設定)
- MediaStoreクエリでCASE式(DATE_TAKEN優先/DATE_ADDEDフォールバック)をBETWEEN条件に使う場合、selectionArgsは`CAST(? AS INTEGER)`で明示的にキャストすること。素の`?`(TEXT型バインド)だと、SQLiteの型比較規則でINTEGER値のCASE式がTEXTより常に小さいと判定され、0件になる不具合があった

## 進捗ログ
- [x] 権限リクエスト(READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE)の最小実装
  - `AndroidManifest.xml` に権限宣言追加
  - `MainActivity.kt` に権限リクエストボタン + 許可状態表示のCompose UI追加
  - `app_name` を仕様通り "kk Photos" に修正
- [x] MediaStoreクエリ(期間指定、DATE_TAKEN/DATE_ADDEDフォールバック)で該当件数を表示
  - 開始日/終了日をMaterial3 DatePickerで選択、ローカルタイムゾーンの日境界で範囲を計算
  - CASE式 + CAST(? AS INTEGER)でクエリ(上記メモ参照)
- [x] リサイズ処理(面積指定モードのみ。長辺指定モードは未実装)
  - `PhotoQuery.kt`: MediaStore範囲クエリ(PhotoEntryのリストを返す)
  - `ImageResizer.kt`: inSampleSizeで粗デコード→createScaledBitmapで目標メガピクセル数に縮小、JPEG品質92で自領域(getExternalFilesDir/Pictures/resized)に保存。元画像が目標より小さい場合は拡大しない
  - プリセット: 小(0.3Mpx)/中(1Mpx)/大(2Mpx) + カスタム入力(FilterChipで選択)
  - 実機で129件処理して成功129件/失敗0件、出力1152x868(≈1Mpx)を確認済み
  - 既知の未対応: 長辺指定モード、Room重複スキップは未着手
- [x] EXIF/XMP保持コピー
  - `ExifCopier.kt`: androidx.exifinterface 1.4.2を使用。ExifInterfaceのTAG_*定数を反射で列挙し全タグを対象にコピー
  - TAG_XMPはgetAttributeBytes/UTF-8文字列化してsetAttribute(ExifInterface.getAttribute(TAG_XMP)は使わない)
  - サムネイルのオフセット/長さタグ(TAG_JPEG_INTERCHANGE_FORMAT系)は別ファイルに持ち込むと壊れるため除外
  - リサイズで実寸法が変わるため、PixelXDimension/PixelYDimension/ImageWidth/ImageLengthは新サイズで上書き
  - ビットマップ自体は回転していないため、Orientationタグは元の値のままコピー(スペックの「回転済みなら1にリセット」は非該当)
  - 実機でexiftool検証済み: Make/Model/DateTimeOriginal/露出情報/GPS/Orientation/XMPが正しくコピーされ、寸法タグも新サイズに補正されていることを確認
  - GPS: 通常のContentResolver.openInputStreamはスコープドストレージにより位置情報Exifをリダクションする(全て0になる)。ACCESS_MEDIA_LOCATION権限を追加し、権限がある場合のみMediaStore.setRequireOriginal(uri)で元データを取得するよう修正。実機でGPS座標が原本と完全一致することを確認済み
    - 既存ユーザーは新権限に気づかず素通りする問題があったため、写真アクセス権限は許可済みだがACCESS_MEDIA_LOCATIONが未許可の場合に画面上部にバナー表示して追加リクエストできるようにした(MainActivity.kt)
  - Ultra HDRガインマップ: 当初「リサイズ後は副画像を保持できないのでXMPごと除去する」対応をしたが、実機検証の結果、この端末のAndroidバージョンではBitmapFactory/Bitmap.compressがUltra HDRガインマップをネイティブに認識し、リサイズ後のガインマップを自動再生成して正しいXMP付きで埋め込むことを確認(exiftoolでガインマップ画像を実際に抽出して確認)。ExifCopier.ktの除去ロジックは「元のXMPをコピーして上書きしない」安全策として温存(この端末では実際には発火しないが、ガインマップを扱えない環境への保険)
  - TAG_IMAGE_WIDTH/TAG_IMAGE_LENGTH/TAG_COMPRESSIONはコピー対象から除外(ExifCopier.kt): ExifInterfaceはJPEGで実タグが無くても実ピクセルサイズから合成した値をgetAttributeで返すため、これを愚直にsetAttributeすると「実在するIFD0タグ」として書き込まれ、リサイズ後の実サイズと食い違う(かつJPEGのIFD0では非標準)。実サイズはPixelXDimension/PixelYDimensionのみで表現する
  - 既知の軽微な制限: androidx.exifinterfaceのsaveAttributes()はIFDエントリをタグID順にソートせず書き込むため、exiftool -validateで「out of sequence」警告が出る(実機で58件)。値自体は全て正しく読み取れており実害はない
- [x] 自領域保存(リサイズ処理の一部としてgetExternalFilesDir/Pictures/resizedに保存済み。上記参照)
- [x] 重複スキップ(Room)
  - キーは「MediaStoreの_ID × リサイズ設定(resizeKey、例: "area:0.3")」の組み合わせ。設定を変えれば別物として再処理される
  - 変更検知は_ID+ファイルサイズ+date_modifiedの組み合わせ(ハッシュ計算は500枚超でI/Oコストが無視できないため見送り)
  - `ProcessedPhotoDatabase.kt`: Room Entity/DAO/Database、`partitionByProcessedState()`で未処理/スキップ対象に振り分け
  - 「件数を確認」後、選択中のリサイズ設定に対する未処理/スキップ予定件数を実行前にプレビュー表示(プリセット変更にもリアクティブに追従)。リサイズ実行後の結果にも成功/失敗/スキップ件数を表示
  - Room導入に伴いandroidx.room 2.8.4 + KSP 2.2.10-2.0.2を追加。AGP 9.xの「built-in Kotlin」とKSPのkotlin.sourceSets DSL利用が非互換のため、gradle.propertiesに`android.disallowKotlinSourceSets=false`を設定(AGP 10.0でオプトアウト自体が廃止予定なので要注意)
  - 実機で「1回目: 成功130/失敗0/スキップ0」→「同条件で2回目: 未処理0/スキップ130」→「設定を中に変更: 未処理130/スキップ0」を確認済み
  - ハマった点: LaunchedEffectのキーにmatchedPhotos(データクラスのリスト)を使うと、中身が前回と構造的に同じ場合は再発火しない。DBの状態だけが変わったケースを拾えなかったため、押下のたびに増分するqueryTokenを別途キーに追加した
- [ ] SAF書き出し
- [ ] 共有シート(送信/受信)
