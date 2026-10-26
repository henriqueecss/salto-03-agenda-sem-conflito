Output docs/demo.gif

Set Shell "powershell"
Set FontSize 18
Set Width 1200
Set Height 520
Set Padding 20
Set Theme "Dracula"
Set TypingSpeed 45ms

Hide
Type "function prompt { 'PS> ' }; Clear-Host"
Enter
Sleep 1s
Show

Type "# 50 pacientes pedem o mesmo horario, ao mesmo tempo"
Enter
Sleep 1500ms
Type ".\scripts\demo-concorrencia.ps1"
Enter
Sleep 7s
