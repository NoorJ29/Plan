#!/usr/bin/env bash
# Installs the latest Borealis APK on an emulator, walks through the main screens,
# and saves screenshots plus any crash log into ./shots.
set -u
PKG=com.borealis.planner
mkdir -p shots

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }

find_node() {  # prints "x y" centre of first node whose text or content-desc matches $1
  for attempt in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    if find_in_dump "$1"; then return 0; fi
    sleep 2
  done
  return 1
}

find_in_dump() {
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

add_task() {  # $1 plain text; typed word by word like a person would
  adb shell am start -a com.borealis.planner.action.ADD -n $PKG/.MainActivity >/dev/null
  sleep 3
  local first=1
  for w in $1; do
    [ "$first" = 1 ] || adb shell input keyevent 62
    adb shell input text "'$w'"   # quoted so the device shell keeps # and !
    first=0
    sleep 0.7
  done
  sleep 1
}

adb install -r Borealis.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
shot 01-first-launch 7

add_task "Send the proposal 11am !!!"
shot 02-editor-quick-parse 1
adb shell input keyevent 66
sleep 3
for t in "Gym 6pm !!" "Call mum at 7" "Read 20 pages" "Water plants 9:30" "Book flights tomorrow 10am"; do
  add_task "$t"; adb shell input keyevent 66; sleep 3
done
shot 03-plan-with-tasks 3

swipe_text "Water plants" 700
shot 04-after-swipe-done 3
tap_text "Gym"
shot 05-card-expanded 2

tap_text "Tomorrow"
shot 06-tomorrow 3
tap_text "Today"
sleep 1

# ---- categories and subcategories ----
tap_text "Add categories"
sleep 2
tap_text "New category"; sleep 1
adb shell input text Uni; adb shell input keyevent 66; sleep 1
tap_text "Add subcategory"; sleep 1
adb shell input text BIO101; adb shell input keyevent 66; sleep 1
tap_text "New category"; sleep 1
adb shell input text Home; adb shell input keyevent 66; sleep 1
shot 06a-category-manager 2
adb shell input keyevent 4; sleep 1
adb shell input keyevent 4; sleep 2

for t in "Revise notes #uni 4pm" "Groceries #home 6pm" "Pay rent #money !"; do
  add_task "$t"; adb shell input keyevent 66; sleep 3
done
add_task "Lab report #bio101 !!"
shot 06b-editor-tag 1
adb shell input swipe 540 1300 540 500 300; sleep 1
adb shell input swipe 540 1300 540 500 300; sleep 1
tap_text "Add a step"; sleep 1
adb shell input text Intro; adb shell input keyevent 66; sleep 1
adb shell input text Method; adb shell input keyevent 66; sleep 1
adb shell input swipe 540 1300 540 500 300; sleep 1
shot 06c-editor-steps 1
tap_text "Add to today"
sleep 3
adb shell am start -n $PKG/.MainActivity >/dev/null
shot 06d-plan-grouped 3
adb shell input swipe 540 1800 540 900 400
shot 06e-plan-grouped-lower 2
adb shell input swipe 540 900 540 1900 400
sleep 1
tap_text "Uni"
shot 06f-filter-uni 2
tap_text "All"
sleep 1

tap_text "Recap"
shot 07-recap 3
tap_text "Widgets"
shot 08-widgets-studio 3
tap_text "Nebula"
shot 09-theme-nebula 2
tap_text "Aurora"
sleep 1
adb shell input swipe 540 1900 540 600 400
sleep 1
adb shell input swipe 540 1900 540 600 400
sleep 1
tap_text "Add Today checklist"
shot 10-pin-dialog 3
tap_text "Add to home screen"
sleep 4
for name in "Add Progress orb"; do
  adb shell am start -n $PKG/.MainActivity >/dev/null
  sleep 3
  adb shell input swipe 540 1900 540 600 400
  sleep 1
  adb shell input swipe 540 1900 540 600 400
  sleep 1
  tap_text "$name"
  sleep 3
  tap_text "Add to home screen"
  sleep 4
done
adb shell input keyevent 3
sleep 2
adb shell input keyevent 3
sleep 2
shot 11a-home-page0 1
for i in 1 2 3; do
  adb shell input swipe 950 1300 120 1300 300
  shot "11-home-page$i" 4
done
tap_text "Tomorrow"
shot 11b-widget-tomorrow 4
tap_text "Today"
sleep 2
tap_text "All"
shot 11c-widget-category 4
tap_text "Uni"
sleep 2
tap_text "BIO101"
sleep 2
adb shell dumpsys appwidget | grep -E "provider=|id=" | head -20 > shots/appwidgets.txt || true

adb shell am start -n $PKG/.MainActivity >/dev/null
sleep 5
tap_text "Settings"
shot 12-settings 3
adb shell input swipe 540 1900 540 500 400
sleep 1
adb shell input swipe 540 1900 540 500 400
shot 12b-settings-app 2
tap_text "Check"
shot 12c-update-check 5
adb shell input swipe 540 600 540 1900 400
sleep 1
adb shell input swipe 540 600 540 1900 400
sleep 1
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
