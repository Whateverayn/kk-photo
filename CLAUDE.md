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
- **パフォーマンス調査は必ずdebuggable=falseのビルドで行うこと**: `debuggable=true`(Android StudioのRunボタンで入るdebugビルド)はAndroid公式ドキュメントに「デバッグ機能をサポートするためランタイム性能を著しく低下させる」と明記されており、公式のMacrobenchmarkツールもdebuggableビルドの計測自体を拒否する仕様になっている。日常の動作確認はdebugビルドで問題ないが、「カクつき」等の体感パフォーマンスの懸念を追いかけるときは、まず`./gradlew installRelease`でreleaseビルド(後述のBaseline Profile進捗ログ参照)を入れて確認すること。debugビルドのカクつきを「仕様のバグ」として追いかけてしまうと無駄な調査になりやすい
- 日常利用: `./gradlew installRelease`でdebuggable=false・署名済み(debug鍵流用)のビルドを実機にインストールできる(`app/build.gradle.kts`のrelease buildTypeにsigningConfigを設定済み)。普段の開発確認はdebugビルドのままでよい

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
- [x] 共有シート(送信側のみ。受信側は未着手)
  - v1: 130枚など大量の写真を無選択でまとめて共有するのはUX的に危険という指摘を受け、
    「直前のリサイズ結果から選んで共有」UIを追加(SharePhotosScreen.ktのSharePhotosSection)
  - FileProvider設定(`res/xml/file_paths.xml`、AndroidManifest.xmlの`<provider>`)。authorityは`${applicationId}.fileprovider`
  - 1件ならACTION_SEND、複数ならACTION_SEND_MULTIPLEを使い分けてchooserを起動
  - 受け取り側(intent-filterでACTION_SEND登録)は未着手
- [x] **UIをギャラリー方式に刷新**(DateRangeQueryScreenを廃止し置き換え)
  - 「期間指定→リサイズ設定→一括実行→結果から共有選択」という一括処理フローから、
    「ギャラリーを閲覧→選びたい写真を直接選択→共有(未変換なら自動変換)」という
    ガレリー的なフローに変更。ユーザーから「一括で全部変換してから選ぶのは逆で、
    最初から選べた方がいい」という指摘を受けて方向転換
  - `GalleryScreen.kt`: 日付範囲フィルタ(既存のDatePicker+MediaStoreクエリをそのまま流用) +
    元画像のサムネイルグリッド(`ContentResolver.loadThumbnail()`で効率的に取得、200px)
  - 日付範囲を変更すると自動でグリッドが再読み込みされる(以前の「件数を確認」ボタン式から
    ブラウジング的な自動更新に変更)
  - リサイズ設定(プリセット/カスタム)は「共有ボタンを押した時点のアクション設定」という位置づけに変更。
    一括の事前リサイズ実行ステップは廃止
  - `resolveOutputFile()`(ProcessedPhotoDatabase.kt): 選択した写真ごとに、既にそのリサイズ設定で
    処理済み(サイズ/更新日時が一致)ならRoomの記録から出力ファイルを再利用、未処理なら
    その場でresizeAndSave()して記録してから共有に使う。重複スキップの仕組みをそのまま活用
  - v1のフィルタは日付範囲のみ。ディレクトリ/アルバム、カメラ機種による絞り込みは将来検討
    (ユーザーとの合意: 複雑になるなら日付のみでOKという方針)
  - 実機で516件のグリッド表示→2件選択→共有ボタンで自動変換→共有チューザー起動
    (`com.android.intentresolver.ChooserActivityLauncher`にフォーカスが移ることで確認)を確認済み
  - MainActivity.ktは権限画面のみに縮小。DateRangeQueryScreenと日付ユーティリティ関数は削除
    (GalleryScreen.kt内にファイルプライベートで再実装。クロスファイルで共有する必要が生じたら
    共通化を検討)
- [x] ギャラリーのサムネイル読み込みをCoilに置き換え(パフォーマンス改善)
  - 自前のBitmapデコード(produceState + ContentResolver.loadThumbnail)にはメモリキャッシュが無く、
    スクロールで画面外に出た写真が戻ってくるたびに毎回デコードし直していたのがカクつきの原因だった
  - 「車輪の再発明はやめて標準的な画像読み込みライブラリを使うべき」という指摘を受けCoilを導入
  - io.coil-kt.coil3:coil-compose、content://のMediaStore Uriをそのまま`AsyncImage`に渡すだけで
    メモリキャッシュ・サイズに応じた自動ダウンサンプリング・リクエストのキャンセルを任せられる
  - **バージョン選定で詰まった点**: 最新3.6.0はkotlin-stdlibを2.4.10に引き上げようとし、
    プロジェクトのKotlin 2.2.10(コンパイラが読めるメタデータ上限は2.3.0)と非互換でビルド不能。
    Kotlin本体を上げる選択肢はKSPのバージョン(room-compiler用、2.2.10専用ビルド)との整合が
    崩れるため見送り、Coil側のバージョンを`./gradlew :app:dependencies`で段階的に確認して
    「kotlin-stdlibを2.3.x以下に保てる最新版」= 3.4.0(stdlib 2.3.10)に決定
    - 3.3.0: stdlib 2.2.10のまま(安全) / 3.4.0: stdlib 2.3.10(コンパイラの読める上限ぎりぎりで動作確認済み)
      / 3.5.0以降: stdlib 2.4.0(非互換)
  - 実機でスクロールダウン→アップを試し、Coil導入前は毎回再デコードされていたのが、
    導入後は即座に表示される(メモリキャッシュから再利用)ことを確認
  - 教訓: KSPを使うライブラリ(Room等)がある限り、Kotlin本体のバージョンは「使っている
    全KSP系ライブラリが対応済みの版」に事実上縛られる。新しいライブラリを追加する際は
    依存関係解決(`./gradlew :app:dependencies`、コンパイル不要で軽い)で先にkotlin-stdlibの
    要求バージョンを確認すると手戻りが少ない
- [x] **解決: 起動直後のギャラリースクロールのカクつき**
  - 症状: アプリ起動直後だけスクロールがカクつく。一度スクロールし終えると滑らかになる
    (WhatsApp/LINE並み)。新しい日付範囲を選んで未経験の写真を表示させても、既にスクロール
    済みの状態からなら滑らかにスクロールできる、という手がかりがあった
  - 効果があった実装変更(カクつきの根本原因ではなかったが、正しい改善ではあるので温存):
    - `SubcomposeAsyncImage` + 常時回転する`CircularProgressIndicator`を撤去
      (Coil公式KDocに「LazyRow/LazyColumn内では使うな、subcompositionが遅い」と明記されていた)
    - カスタムCoil Fetcher(`MediaStoreThumbnailFetcher.kt`)導入: Coil標準の
      `ContentUriFetcher`は元画像を毎回自前でBitmapFactoryデコードしOSのサムネイルキャッシュを
      使わないため、`ContentResolver.loadThumbnail()`を使うFetcherに差し替えた
  - **真の原因が判明**: JIT/シェーダーウォームアップという仮説を検証するためBaseline Profile
    (下記エントリ参照)を導入し、実機で「Profile無し(AOT無効化・verify)」と「Profile有り
    (speed-profileでAOT強制)」をA/Bテストしたところ、**両方とも同じくらい滑らかで体感差が
    無かった**。一方、この検証は必然的にdebugビルドではなくreleaseビルド(`nonMinifiedRelease`)
    で行っていたため、そこで初めて「releaseビルド(debuggable=false)にした時点でカクつきが
    完全に消える」ことに気づいた。Android公式ドキュメントに「debuggable=trueはデバッグ機能の
    ためランタイム性能を著しく低下させる」と明記されており、公式Macrobenchmarkツールも
    debuggableビルドの計測自体を拒否する仕様になっている。**つまり原因はJIT/シェーダー
    ウォームアップではなく、単純にAndroid StudioのRunボタンで入るdebugビルド(debuggable=true)
    を使い続けていたことだった。** 「新しい写真でもスクロール後なら滑らか」という現象自体は、
    debuggableビルドでもART側のJITが実行頻度の高いコードパス(Composeのグリッド描画・
    Coilのデコード処理そのもの)を段階的にウォームアップしていくためと考えられ、矛盾しない
    (ただし体感の「底」自体がdebuggableのせいで低かった、という話)
  - 教訓: パフォーマンス体感の調査は必ずreleaseビルド(debuggable=false)で行うこと。詳細は
    上記「開発メモ」参照
  - 計測方法メモ: `adb shell dumpsys gfxinfo <package> reset`+`adb shell input swipe`+
    `dumpsys gfxinfo`でJanky frames率を計測できるが、1回のスワイプで20-70フレーム程度しか
    取れずサンプル数が少なく結果がブレやすい。今回は実機でユーザー自身が指で触った体感の方が
    最終的に信頼できる判断材料になった
- [x] **Baseline Profile導入**(上記カクつき調査から派生)
  - Android Studioの「Baseline Profile Generator」モジュールウィザードで`baselineprofile`
    モジュールを作成。`androidx.benchmark`のMacrobenchmark(`BaselineProfileRule`)で
    実機の起動シーケンスを計測し、AOTコンパイル対象コードのリスト(`baseline-prof.txt`)を
    自動生成する仕組み
  - **ハマった点**: ウィザードが書き込んだバージョン(`androidx.baselineprofile`
    Gradleプラグイン1.2.4)が、プロジェクトのAGP 9.3.2と非互換で
    `UnknownDomainObjectException: Extension of type 'TestExtension' does not exist`エラー。
    公式リリースノートを確認したところ、AGP 9.0で`com.android.test`モジュールの内部構造が
    刷新され(newDsl)、対応したのは1.5.0-alpha01(2025-12-17)以降のみだった。まだ正式版(GA)
    ではないが、この機能が必要なため`androidx.benchmark`/`androidx.baselineprofile`を
    1.5.0-rc02に、それに伴い`androidx.test.uiautomator`を2.4.0に引き上げて解決
    (`androidx.profileinstaller`は1.4.1安定版のまま)
  - `BaselineProfileGenerator.kt`のジャーニー: ベンチマーク用ビルドは権限が未許可の状態で
    インストールされるため、`InstrumentationRegistry`経由のシェルコマンドで
    READ_MEDIA_IMAGES/ACCESS_MEDIA_LOCATIONを起動前に付与 → `pressHome()`→
    `startActivityAndWait()` → `UiDevice.swipe()`でギャラリーグリッドを5回スクロール
  - `app/build.gradle.kts`のreleaseビルドタイプに`signingConfig = signingConfigs.getByName("debug")`
    を追加(個人利用アプリで配布要件がないためdebug鍵を流用)。これにより`./gradlew installRelease`
    で日常利用向けのdebuggable=false・署名済みビルドを気軽にインストールできる
  - **実機A/Bテストの結果**: 「AOT無効(verify)+プロファイルクリア」と「AOT有効(speed-profile、
    Baseline Profile適用)」を切り替えて比較したが、ユーザーの体感では**差がわからなかった**。
    上記の通りdebuggable=false自体で既にカクつきが解消していたため、今回のアプリ規模では
    Baseline Profileの上乗せ効果は体感できるレベルではないという結論。それでも公式推奨の
    標準的な仕組みであり維持コストも低いため、モジュール自体は残す方針(将来アプリの規模が
    増えた場合に効いてくる可能性がある)
  - 既知の軽微な警告: `installRelease`実行時にD8から`Startup method not found`警告が
    大量に出る(`GalleryScreenKt$$ExternalSyntheticLambda*`など)。プロファイル生成時の
    ビルド(`nonMinifiedRelease`)と適用先のビルド(`release`)で、コンパイラが生成する
    ラムダ用のsyntheticクラス名の採番が一致しないために起きる。ビルドは成功しており、
    影響は`startup-prof.txt`(dexレイアウト最適化)の一部エントリが無視される程度に限られる
    (メインのbaseline-prof.txtによるAOT自体には影響なし)。実害小さく、追いかけていない
