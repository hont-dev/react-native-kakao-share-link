package com.reactnativekakaosharelink

import android.content.ActivityNotFoundException
import com.facebook.react.bridge.*
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.KakaoCustomTabsClient
import com.kakao.sdk.link.LinkClient
import com.kakao.sdk.link.WebSharerClient
import com.kakao.sdk.template.model.*

class KakaoShareLinkModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return "KakaoShareLink"
  }

  private fun getS(dict: ReadableMap, key: String): String? {
    return if (dict.hasKey(key)) dict.getString(key) else null
  }

  private fun getI(dict: ReadableMap, key: String): Int? {
    return if (dict.hasKey(key)) dict.getInt(key) else null
  }

  private fun createExecutionParams(dictArr: ReadableArray?): Map<String, String>? {
    if (dictArr == null) return null
    val length = dictArr.size() - 1
    if (length == -1) return null
    var map = mutableMapOf<String, String>();
    for (i in 0..length) {
      val dict: ReadableMap = dictArr.getMap(i)!!
      val key = getS(dict, "key")!!
      val value = getS(dict, "value")!!
      map.put(key, value)
    }
    return map.toMap()
  }

  private fun createLink(dict: ReadableMap): Link {
    val webURL: String? = getS(dict, "webUrl")
    val mobileWebURL: String? = getS(dict, "mobileWebUrl")
    val iosExecutionParams: Map<String, String>? = createExecutionParams(dict.getArray("iosExecutionParams"))
    val androidExecutionParams: Map<String, String>? = createExecutionParams(dict.getArray("androidExecutionParams"))
    return Link(webUrl = webURL, mobileWebUrl = mobileWebURL, iosExecutionParams = iosExecutionParams, androidExecutionParams = androidExecutionParams)
  }

  private fun createButton(dict: ReadableMap): Button {
    val title: String = getS(dict, "title")!!
    val link: Link = createLink(dict.getMap("link")!!)
    return Button(title, link)
  }

  private fun createButtons(dictArr: ReadableArray): List<Button>? {
    val length = dictArr.size() - 1
    val buttons = mutableListOf<Button>()
    if (length < 0) return null
    for (i in 0..length) {
      buttons.add(createButton(dictArr.getMap(i)!!))
    }
    return buttons.toList()
  }

  private fun createContent(dict: ReadableMap): Content {
    val title: String = getS(dict, "title")!!
    val url: String = getS(dict,"imageUrl")!!
    val link: Link = createLink(dict.getMap("link")!!)
    val desc: String? = getS(dict,"description")
    val imgWidth: Int? = getI(dict,"imageWidth")
    val imgHeight: Int? = getI(dict,"imageHeight")
    return Content(title, description = desc, imageUrl = url, imageWidth = imgWidth, imageHeight = imgHeight, link = link)
  }

  private fun createContents(dictArr: ReadableArray): List<Content> {
    val length = dictArr.size() - 1
    val contents = mutableListOf<Content>();
    if (length < 0) return contents
    for (i in 0..length) {
      contents.add(createContent(dictArr.getMap(i)!!))
    }
    return contents.toList()
  }

  private fun createCommerce(dict: ReadableMap): Commerce {
    val regularPrice: Int = getI(dict,"regularPrice")!!
    val discountPrice: Int? = getI(dict, "discountPrice")
    val discountRate: Int? = getI(dict, "discountRate")
    val fixedDiscountPrice: Int? = getI(dict,"fixedDiscountPrice")
    val productName: String? = getS(dict,"productName")
    return Commerce(regularPrice, discountPrice, fixedDiscountPrice, discountRate, productName)
  }

  private fun createSocial(dict: ReadableMap): Social {
    val commentCount: Int? = getI(dict, "commentCount")
    val likeCount: Int? = getI(dict,"likeCount")
    val sharedCount: Int? = getI(dict, "sharedCount")
    val subscriberCount: Int? = getI(dict, "subscriberCount")
    val viewCount: Int? = getI(dict, "viewCount")
    return Social(likeCount, commentCount, sharedCount, viewCount, subscriberCount)
  }

  private fun sendWithTemplate(template: DefaultTemplate, promise: Promise) {
    val serverCallbackArgs = HashMap<String, String>()
    serverCallbackArgs["user_id"] = "\${current_user_id}"
    serverCallbackArgs["product_id"] = "\${shared_product_id}"
    if (LinkClient.instance.isKakaoLinkAvailable(this.reactContext)) {
      LinkClient.instance.defaultTemplate(reactContext, template, serverCallbackArgs) { linkResult, error ->
        if (error != null) {
          promise.reject("E_KAKAO_ERROR", error.message, error)
          return@defaultTemplate
        } else {
          val map = Arguments.createMap()
          map.putBoolean("result", true)
          map.putString("intent", linkResult?.intent.toString())
          linkResult?.intent?.let { intent -> reactContext.startActivity(intent, null) }
          map.putString("warning", linkResult?.warningMsg.toString())
          map.putString("argument", linkResult?.argumentMsg.toString())
          map.putString("callback", serverCallbackArgs.toString())
          promise.resolve(map)
          return@defaultTemplate
        }
      }
    } else {
      // 카카오톡 미설치: 웹 공유 사용 권장
      // 웹 공유 예시 코드
      // 220615 카카오톡이 설치되지않은 android 에서 앱꺼짐 현상이 발생
      Toast.makeText(reactContext, "카카오톡이 설치되어있지 않습니다.", Toast.LENGTH_SHORT).show()

      // // 카카오톡 미설치: 웹 공유 사용 권장
      // // 웹 공유 예시 코드
      // val sharerUrl = WebSharerClient.instance.defaultTemplateUri(template, serverCallbackArgs)
      // Log.d("kakaotalk installed sharerUrl", sharerUrl.toString())
      // // 1. CustomTabs으로 Chrome 브라우저 열기
      // try {
      //  KakaoCustomTabsClient.openWithDefault(reactContext, sharerUrl)
      // } catch (e: UnsupportedOperationException) {
      // // 2. CustomTabs으로 디바이스 기본 브라우저 열기
      //  try {
      //    KakaoCustomTabsClient.open(reactContext, sharerUrl)
      //  } catch (e: ActivityNotFoundException) {
      // // 인터넷 브라우저가 없을 때 예외처리
      //    promise.reject("E_KAKAO_NO_BROWSER", e.message, e)
      //  }
      // }
    }
  }

  @ReactMethod
  private fun sendCommerce(dict: ReadableMap, promise: Promise) {
    val commerce = CommerceTemplate(
      content = createContent(dict.getMap("content")!!),
      commerce = createCommerce(dict.getMap("commerce")!!),
      buttons = if (dict.hasKey("buttons")) createButtons(dict.getArray("buttons")!!) else null,
      buttonTitle = getS(dict, "buttonTitle")
    )
    sendWithTemplate(commerce, promise)
  }

  @ReactMethod
  private fun sendList(dict: ReadableMap, promise: Promise) {
    val list = ListTemplate(
      headerTitle = if (dict.hasKey("headerTitle")) dict.getString("headerTitle")!! else "",
      headerLink = createLink(dict.getMap("headerLink")!!),
      contents = createContents(dict.getArray("contents")!!),
      buttons = if (dict.hasKey("buttons")) createButtons(dict.getArray("buttons")!!) else null,
      buttonTitle = getS(dict, "buttonTitle")
    )
    sendWithTemplate(list, promise)
  }

  @ReactMethod
  private fun sendFeed(dict: ReadableMap, promise: Promise) {
    val feed = FeedTemplate(
      content = createContent(dict.getMap("content")!!),
      social = if (dict.hasKey("social")) createSocial(dict.getMap("social")!!) else null,
      buttons = if (dict.hasKey("buttons")) createButtons(dict.getArray("buttons")!!) else null,
      buttonTitle = getS(dict, "buttonTitle")
    )
    sendWithTemplate(feed, promise)
  }

  @ReactMethod
  private fun sendLocation(dict: ReadableMap, promise: Promise) {
    val location = LocationTemplate(
      address = if (dict.hasKey("address")) dict.getString("address")!! else "",
      addressTitle = if (dict.hasKey("addressTitle")) dict.getString("addressTitle")!! else null,
      content = createContent(dict.getMap("content")!!),
      social = if (dict.hasKey("social")) createSocial(dict.getMap("social")!!) else null,
      buttons = if (dict.hasKey("buttons")) createButtons(dict.getArray("buttons")!!) else null,
      buttonTitle = getS(dict, "buttonTitle")
    )
    sendWithTemplate(location, promise)
  }

  @ReactMethod
  private fun sendText(dict: ReadableMap, promise: Promise) {
    val text = TextTemplate(
      text = if (dict.hasKey("text")) dict.getString("text")!! else "",
      link = createLink(dict.getMap("link")!!),
      buttons = if (dict.hasKey("buttons")) createButtons(dict.getArray("buttons")!!) else null,
      buttonTitle = getS(dict, "buttonTitle")
    )
    sendWithTemplate(text, promise)
  }

  @ReactMethod
  private fun sendCustom(dict: ReadableMap, promise: Promise) {
    val templateId = if (dict.hasKey("templateId")) dict.getInt("templateId")!! else 0
    val templateArgs = createExecutionParams(dict.getArray("templateArgs"))
    val serverCallbackArgs = HashMap<String, String>()
    serverCallbackArgs["user_id"] = "\${current_user_id}"
    serverCallbackArgs["product_id"] = "\${shared_product_id}"

    if (LinkClient.instance.isKakaoLinkAvailable(reactContext)) {
      LinkClient.instance.customTemplate(reactContext, templateId = templateId.toLong(), templateArgs = templateArgs, serverCallbackArgs = serverCallbackArgs) {
        linkResult, error ->
        if (error != null) {
          promise.reject("E_KAKAO_ERROR", error.message, error)
          return@customTemplate
        } else {
          val map = Arguments.createMap()
          map.putBoolean("result", true)
          map.putString("intent", linkResult?.intent.toString())
          linkResult?.intent?.let { intent -> reactContext.startActivity(intent, null) }
          map.putString("warning", linkResult?.warningMsg.toString())
          map.putString("argument", linkResult?.argumentMsg.toString())
          map.putString("callback", serverCallbackArgs.toString())
          promise.resolve(map)
          return@customTemplate
        }
      }
    } else {
      // 카카오톡 미설치: 웹 공유 사용 권장
      // 웹 공유 예시 코드
      val sharerUrl = WebSharerClient.instance.customTemplateUri(templateId.toLong(), templateArgs = templateArgs)

      // 1. CustomTabs으로 Chrome 브라우저 열기
      try {
        KakaoCustomTabsClient.openWithDefault(reactContext, sharerUrl)
      } catch (e: UnsupportedOperationException) {
        // 2. CustomTabs으로 디바이스 기본 브라우저 열기
        try {
          KakaoCustomTabsClient.open(reactContext, sharerUrl)
        } catch (e: ActivityNotFoundException) {
          // 인터넷 브라우저가 없을 때 예외처리
          promise.reject("E_KAKAO_NO_BROWSER", e.message, e)
        }
      }
    }
  }

  init {
    val kakaoAppKey = reactContext.resources.getString(
      reactContext.resources.getIdentifier("kakao_app_key", "string", reactContext.packageName))
    KakaoSdk.init(reactContext, kakaoAppKey)
  }
}
