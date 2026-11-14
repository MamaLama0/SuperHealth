'use strict';
// Android WebView can retain a zero CSS viewport height when Compose first lays out the view.
function sizeViewport() {
  document.documentElement.style.setProperty('--view-height', Math.max(1, window.innerHeight)+'px');
}
window.addEventListener('resize',sizeViewport,{passive:true});
sizeViewport();
