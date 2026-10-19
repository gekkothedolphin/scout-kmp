#!/bin/bash
#
# Checks the license header of every *.kt file in the repository.
#
# Three header templates are accepted, depending on where the file lives:
#   scout/**            license_header           (original Yandex header; scout/ is frozen)
#   everything else     license_header_modified  (file copied from scout/ and adapted:
#                                                 Yandex header + "Modifications copyright")
#                       license_header_fork      (file written from scratch in the fork)
#
# Skipped: build outputs (*/build/*, .gradle, .kotlin) and throwaway experiments
# (.ai/experiments/*).
#
# --correct only adds headers where the right one is unambiguous:
#   scout/**        missing header -> license_header is prepended (original behaviour)
#   elsewhere       no header at all -> license_header_fork is prepended
#                   a Yandex header without the "Modifications copyright" line is
#                   reported, never rewritten automatically

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd)"
repository_root="$(dirname "$script_dir")"

correct=false

while :; do
    case $1 in
        -c|--correct)
        correct=true
        ;;
        *) break
    esac
    shift
done

license_original=$(cat "${script_dir}/license_header")
license_modified=$(cat "${script_dir}/license_header_modified")
license_fork=$(cat "${script_dir}/license_header_fork")
lines_original=$(printf '%s\n' "$license_original" | wc -l | tr -d ' ')
lines_modified=$(printf '%s\n' "$license_modified" | wc -l | tr -d ' ')
lines_fork=$(printf '%s\n' "$license_fork" | wc -l | tr -d ' ')

fails=0

fail() {
    fails=$((fails + 1))
    >&2 echo "Error: $1 in file $2"
}

prepend() {
    local header="$1" file="$2"
    printf '%s\n%s\n' "$header" "$(cat "$file")" > "$file"
    echo "Note: Added license header to file $file"
}

while IFS= read -r -d '' file; do
    relative="${file#"$repository_root"/}"

    if [[ "$relative" == scout/* ]]; then
        if [ "$(head -n "$lines_original" "$file")" != "$license_original" ]; then
            if [ "$correct" = true ]; then
                prepend "$license_original" "$file"
            else
                fail "Missing original license header" "$file"
            fi
        fi
        continue
    fi

    if [ "$(head -n "$lines_modified" "$file")" == "$license_modified" ] ||
       [ "$(head -n "$lines_fork" "$file")" == "$license_fork" ]; then
        continue
    fi

    if head -n 5 "$file" | grep -q "Yandex LLC"; then
        fail "Yandex header without the 'Modifications copyright' line (use scripts/license_header_modified for code copied from scout/)" "$file"
    elif head -n 5 "$file" | grep -qi "copyright"; then
        fail "Unrecognized license header (expected scripts/license_header_modified or scripts/license_header_fork)" "$file"
    elif [ "$correct" = true ]; then
        prepend "$license_fork" "$file"
    else
        fail "Missing license header" "$file"
    fi
done < <(find "$repository_root" \
    \( -path '*/build/*' -o -path '*/.gradle/*' -o -path '*/.kotlin/*' \
       -o -path "$repository_root/.ai/experiments/*" -o -path "$repository_root/.git/*" \) -prune \
    -o -name '*.kt' -type f -print0)

if [ "$fails" -gt 0 ]; then
    >&2 echo "Failure: Check failed with ${fails} errors. Please fix problems and try again. You also can re-run this script with --correct argument to add missing headers where the right header is unambiguous."
    exit 1
fi
