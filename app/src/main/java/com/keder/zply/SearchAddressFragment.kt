package com.keder.zply

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.keder.zply.databinding.FragmentSearchAddressBinding

class SearchAddressFragment : Fragment() {

    private lateinit var binding: FragmentSearchAddressBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchAddressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val webView = binding.webView

        // 1. 웹뷰 설정 강화
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true // viewport meta tag 적용
            loadWithOverviewMode = true // 컨텐츠가 화면보다 클 경우 화면 크기에 맞춤
            setSupportZoom(false) // 핀치 줌 끄기 (앱처럼 보이게)
        }

        webView.addJavascriptInterface(MyJavaScriptInterface(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // 로딩 완료 시 주소찾기 함수 실행
                webView.loadUrl("javascript:execDaumPostcode();")
            }
        }

        // 2. 개선된 HTML (Viewport 및 CSS Reset 적용)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <!-- 모바일 화면에 꽉 차게 맞추는 핵심 태그 -->
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    /* 웹페이지 기본 여백 제거 (흰 테두리 삭제) */
                    html, body { margin: 0; padding: 0; width: 100%; height: 100%; }
                    /* 터치 시 하이라이트 제거 */
                    * { -webkit-tap-highlight-color: rgba(0,0,0,0); }
                </style>
            </head>
            <body>
                <div id="layer" style="display:none; position:fixed; overflow:hidden; z-index:1; -webkit-overflow-scrolling:touch;"></div>
                <script src="https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script>
                <script>
                    function execDaumPostcode() {
                        var element_layer = document.getElementById('layer');
                        
                        new daum.Postcode({
                            oncomplete: function(data) {
                                var fullAddr = data.address; 
                                var extraAddr = '';
                                if(data.addressType === 'R'){
                                    if(data.bname !== ''){ extraAddr += data.bname; }
                                    if(data.buildingName !== ''){ extraAddr += (extraAddr !== '' ? ', ' + data.buildingName : data.buildingName); }
                                    fullAddr += (extraAddr !== '' ? ' ('+ extraAddr +')' : '');
                                }
                                window.Android.processDATA(fullAddr);
                            },
                            width : '100%',
                            height : '100%'
                        }).embed(element_layer);
                        
                        // 화면에 보이게 설정
                        element_layer.style.display = 'block';
                        
                        // 레이어 크기를 화면 전체로 강제 설정 (JS로 한 번 더 확실하게)
                        initLayerPosition();
                    }

                    function initLayerPosition(){
                        var element_layer = document.getElementById('layer');
                        element_layer.style.width = '100%';
                        element_layer.style.height = '100%';
                        element_layer.style.top = '0px';
                        element_layer.style.left = '0px';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://daum.net", html, "text/html", "UTF-8", null)
    }

    inner class MyJavaScriptInterface {
        @JavascriptInterface
        fun processDATA(address: String?) {
            activity?.runOnUiThread {
                setFragmentResult("request_address", bundleOf("address_data" to address))
                parentFragmentManager.popBackStack()
            }
        }
    }
}