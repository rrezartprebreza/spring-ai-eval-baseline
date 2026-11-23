# Records docs/demo.gif with https://github.com/charmbracelet/vhs
#   brew install vhs && vhs docs/demo.tape
# Shows a green run, then a "worse model" (7 of 10 recorded answers broken) turning the build red.

Output docs/demo.gif
Set FontSize 18
Set Width 1200
Set Height 700
Set TypingSpeed 40ms

Type "mvn -q -pl eval-example -am test"
Enter
Sleep 25s
Type "cat eval-example/target/eval-reports/SupportBotEvalTest.md"
Enter
Sleep 4s
Type "# a prompt change makes most answers forget the 30-day limit:"
Enter
Type "python3 docs/break_answers.py"
Enter
Sleep 2s
Type "mvn -q -pl eval-example -am test 2>&1 | grep -A6 'Eval regression'"
Enter
Sleep 25s
Type "git checkout -- eval-example/src/test/resources"
Enter
Sleep 2s
