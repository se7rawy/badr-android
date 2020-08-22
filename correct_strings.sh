#!/bin/bash

mydir="$(dirname "$(realpath "$0")")"

# Element -> SchildiChat
find "$mydir/vector/src/main/res" -name strings.xml -exec \
    sed -i 's|Element|SchildiChat|g' '{}' \;
# Restore Element where it makes sense
find "$mydir/vector/src/main/res" -name strings.xml -exec \
    sed -i 's/SchildiChat \(Web\|iOS\|Desktop\)/Element \1/g' '{}' \;
find "$mydir/vector/src/main/res" -name strings.xml -exec \
    sed -i 's|SchildiChat Matrix Services|Element Matrix Services|g' '{}' \;
find "$mydir/vector/src/main/res" -name strings.xml -exec \
    sed -i 's|\("use_latest_riot">.*\)SchildiChat\(.*</string>\)|\1Element\2|g' '{}' \;
find "$mydir/vector/src/main/res" -name strings.xml -exec \
    sed -i 's|\("use_other_session_content_description">.*\)SchildiChat\(.*SchildiChat.*</string>\)|\1SchildiChat/Element\2|' '{}' \;

# Requires manual intervention for correct grammar
sed -i 's|!nnen|wolpertinger|g' "$mydir/vector/src/main/res/values-de/strings.xml"
sed -i 's|!n|schlumpfwesen|g' "$mydir/vector/src/main/res/values-de/strings.xml"
