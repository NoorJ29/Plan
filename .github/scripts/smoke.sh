#!/usr/bin/env bash
# Installs the latest Borealis APK on an emulator, walks through the main screens,
# and saves screenshots plus any crash log into ./shots.
set -u
PKG=com.borealis.planner
mkdir -p shots

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }

find_node() {  # prints "x y" centre of first node whose text or content-desc matches $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys
t = sys.argv[1]
x = open('ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    if f'text="{t}"' in n or f'content-desc="{t}"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local xy
  if xy=$(find_node "$1"); then adb shell input tap $xy; echo "tapped $1"; else echo "NOT FOUND: $1"; fi
}

swipe_text() {  # swipe a card horizontally: $1 text, $2 dx
  local xy
  if xy=$(find_node "$1"); then
    set -- $xy "$2"
    adb shell input swipe "$1" "$2" $(( $1 + $3 )) "$2" 350; echo "swiped"
  else echo "NOT FOUND for swipe"; fi
}

add_task() {  # $1 text typed with %s for spaces
  adb shell am start -a com.borealis.planner.action.ADD -n $PKG/.MainActivity >/dev/null
  sleep 3
  adb shell input text "$1"
  sleep 1
}

adb install -r Borealis.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
shot 01-first-launch 7

add_task "Send%sthe%sproposal%s11am%s!!!"
shot 02-editor-quick-parse 1
adb shell input keyevent 66
sleep 2
for t in "Gym%s6pm%s!!" "Call%smum%sat%s7" "Read%s20%spages" "Water%splants%s9:30" "Book%sflights%stomorrow%s10am"; do
  add_task "$t"; adb shell input keyevent 66; sleep 2
done
adb shell input keyevent 4 || true
shot 03-plan-with-tasks 3

swipe_text "Water plants" 700
shot 04-after-swipe-done 3
tap_text "Gym"
shot 05-card-expanded 2

tap_text "Tomorrow"
shot 06-tomorrow 3
tap_text "Today"
sleep 1

tap_text "Recap"
shot 07-recap 3
tap_text "Widgets"
shot 08-widgets-studio 3
tap_text "Nebula"
shot 09-theme-nebula 2
tap_text "Aurora"
sleep 1
tap_text "Add Today checklist"
shot 10-pin-dialog 3
tap_text "Add to home screen" || true
tap_text "Add" || true
sleep 2
adb shell input keyevent 3
shot 11-home-widget 4

adb shell am start -n $PKG/.MainActivity >/dev/null
sleep 2
tap_text "Settings"
shot 12-settings 3
tap_text "Test"
sleep 1
adb shell input keyevent 26   # screen off so the alarm has to wake it
sleep 9
shot 13-alarm-lock-screen 1
tap_text "Looks good"
sleep 2

adb logcat -d -b crash > shots/crash.txt || true
adb logcat -d | grep -E "AndroidRuntime|FATAL|Borealis|planner|Glance|AppWidget" | tail -200 > shots/logcat.txt || true
echo "crash lines: $(wc -l < shots/crash.txt)"
