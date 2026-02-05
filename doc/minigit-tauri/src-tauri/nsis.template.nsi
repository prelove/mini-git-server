!include "MUI2.nsh"
!include "FileFunc.nsh"
!define MUI_ABORTWARNING

!macro preInit
  ; Try java -version (must be available in PATH).
  nsExec::ExecToStack 'cmd /c "java -version"'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_ICONSTOP|MB_OK "Java was not detected (JRE 17+ required). Install it before running the installer.$\nRecommended: https://adoptium.net/temurin/releases/?version=17"
    Abort
  ${EndIf}
!macroend
