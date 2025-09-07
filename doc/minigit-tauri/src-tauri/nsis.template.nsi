!include "MUI2.nsh"
!include "FileFunc.nsh"
!define MUI_ABORTWARNING

!macro preInit
  ; 试运行 java -version（PATH 中必须能找到）
  nsExec::ExecToStack 'cmd /c "java -version"'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_ICONSTOP|MB_OK "未检测到 Java（需要 JRE 17+）。请先安装后再运行安装器。$\n推荐：https://adoptium.net/temurin/releases/?version=17"
    Abort
  ${EndIf}
!macroend
