#!/usr/bin/env bash
#
# install-concept.sh
#
# Distributes the 27 concept paper files into the correct directory structure.
# Expects all files (with or without a path prefix) in one source directory.
#
# Usage:
#   1. Configure the paths below
#   2. Make the script executable: chmod +x install-concept.sh
#   3. Run: ./install-concept.sh
#

set -euo pipefail

# =============================================================================
# CONFIGURATION – adjust these paths before running
# =============================================================================

# Source directory: all 27 .md files are located here
# They can either be flat (e.g. after a download)
# or already contain the subfolder structure.
SOURCE_DIR="${SOURCE_DIR:-./downloaded}"

# Target directory: the finished structure is created here
# Typical: the directory of your git repo, subfolder "concept"
TARGET_DIR="${TARGET_DIR:-./concept}"

# Behavior for existing files:
#   "fail"      – script aborts if the target directory is not empty (default)
#   "overwrite" – existing files are overwritten
#   "backup"    – existing files are saved as .bak before being overwritten
ON_CONFLICT="${ON_CONFLICT:-fail}"

# =============================================================================
# DO NOT ADJUST BELOW THIS LINE
# =============================================================================

# Mapping: file name -> target folder (relative to TARGET_DIR)
# Root files have target folder "" (= directly in TARGET_DIR)
declare -A FILE_MAP=(
    # Root
    ["README.md"]=""
    ["INDEX.md"]=""
    ["CHANGELOG.md"]=""

    # 00-overview
    ["01-positioning.md"]="00-overview"
    ["02-meta-metamodel.md"]="00-overview"

    # 10-foundations
    ["03-framework-relationship.md"]="10-foundations"
    ["04-enterprise-continuum-trm.md"]="10-foundations"
    ["05-principles-standards-adrs.md"]="10-foundations"

    # 20-entities
    ["06-core-entity-types.md"]="20-entities"
    ["07-motivation-stakeholders-goals.md"]="20-entities"
    ["08-organization-roles-persons.md"]="20-entities"
    ["09-process-architecture.md"]="20-entities"
    ["10-cross-cutting.md"]="20-entities"

    # 30-dynamics
    ["11-temporal-model.md"]="30-dynamics"
    ["12-domain-views.md"]="30-dynamics"
    ["13-business-technology-linking.md"]="30-dynamics"

    # 40-extensibility
    ["14-extensibility.md"]="40-extensibility"
    ["15-schema-evolution.md"]="40-extensibility"

    # 50-walkthrough
    ["16-example-walkthrough.md"]="50-walkthrough"

    # 60-integrations
    ["17-itsm-integration.md"]="60-integrations"
    ["18-ppm-integration.md"]="60-integrations"
    ["19-agile-scaling.md"]="60-integrations"
    ["20-grc-gdpr-isms-integration.md"]="60-integrations"

    # 70-platform
    ["21-api-architecture.md"]="70-platform"
    ["22-analyzability.md"]="70-platform"

    # 90-backlog
    ["23-open-issues.md"]="90-backlog"
    ["24-next-steps.md"]="90-backlog"
)

# List of all subdirectories (for mkdir)
SUBDIRS=(
    "00-overview"
    "10-foundations"
    "20-entities"
    "30-dynamics"
    "40-extensibility"
    "50-walkthrough"
    "60-integrations"
    "70-platform"
    "90-backlog"
)

# =============================================================================
# Helper functions
# =============================================================================

log_info()  { printf '\033[0;32m[INFO]\033[0m  %s\n' "$*"; }
log_warn()  { printf '\033[0;33m[WARN]\033[0m  %s\n' "$*" >&2; }
log_error() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*" >&2; }

# Searches for a file in the source directory – flat or in subfolders.
# Prints the first match or an empty string if not found.
find_source_file() {
    local filename="$1"
    find "$SOURCE_DIR" -type f -name "$filename" -print -quit 2>/dev/null
}

# =============================================================================
# Preliminary checks
# =============================================================================

log_info "Configuration:"
log_info "  SOURCE_DIR  = $SOURCE_DIR"
log_info "  TARGET_DIR  = $TARGET_DIR"
log_info "  ON_CONFLICT = $ON_CONFLICT"
echo

if [[ ! -d "$SOURCE_DIR" ]]; then
    log_error "Source directory not found: $SOURCE_DIR"
    log_error "Please configure SOURCE_DIR or create the directory."
    exit 1
fi

if [[ "$ON_CONFLICT" != "fail" && "$ON_CONFLICT" != "overwrite" && "$ON_CONFLICT" != "backup" ]]; then
    log_error "ON_CONFLICT must be 'fail', 'overwrite' or 'backup', but is: $ON_CONFLICT"
    exit 1
fi

# Conflict check if TARGET_DIR exists and is not empty
if [[ -d "$TARGET_DIR" ]] && [[ -n "$(ls -A "$TARGET_DIR" 2>/dev/null || true)" ]]; then
    case "$ON_CONFLICT" in
        fail)
            log_error "Target directory is not empty: $TARGET_DIR"
            log_error "Set ON_CONFLICT=overwrite or ON_CONFLICT=backup to continue anyway."
            exit 1
            ;;
        overwrite)
            log_warn "Target directory is not empty – existing files will be overwritten."
            ;;
        backup)
            log_warn "Target directory is not empty – existing files will be saved as .bak."
            ;;
    esac
fi

# =============================================================================
# Completeness check of the source files
# =============================================================================

log_info "Checking completeness of the source files..."

missing_files=()
for filename in "${!FILE_MAP[@]}"; do
    src=$(find_source_file "$filename")
    if [[ -z "$src" ]]; then
        missing_files+=("$filename")
    fi
done

if [[ ${#missing_files[@]} -gt 0 ]]; then
    log_error "The following files are missing in the source directory:"
    for f in "${missing_files[@]}"; do
        log_error "  - $f"
    done
    log_error "Aborting script. Please provide all files."
    exit 1
fi

log_info "All ${#FILE_MAP[@]} files found."
echo

# =============================================================================
# Create directories
# =============================================================================

log_info "Creating directory structure..."
mkdir -p "$TARGET_DIR"
for subdir in "${SUBDIRS[@]}"; do
    mkdir -p "$TARGET_DIR/$subdir"
done

# =============================================================================
# Copy files
# =============================================================================

log_info "Copying files..."
echo

copied=0
backed_up=0
overwritten=0

for filename in "${!FILE_MAP[@]}"; do
    src=$(find_source_file "$filename")
    target_subdir="${FILE_MAP[$filename]}"

    if [[ -z "$target_subdir" ]]; then
        target="$TARGET_DIR/$filename"
        display_target="$filename"
    else
        target="$TARGET_DIR/$target_subdir/$filename"
        display_target="$target_subdir/$filename"
    fi

    # Conflict handling per file
    if [[ -e "$target" ]]; then
        case "$ON_CONFLICT" in
            backup)
                cp -p "$target" "$target.bak"
                backed_up=$((backed_up + 1))
                ;;
            overwrite)
                overwritten=$((overwritten + 1))
                ;;
            fail)
                # Should never be reached because of the preliminary check, but just to be safe
                log_error "File already exists: $target"
                exit 1
                ;;
        esac
    fi

    cp -p "$src" "$target"
    copied=$((copied + 1))
    printf "  %-44s -> %s\n" "$filename" "$display_target"
done

# =============================================================================
# Summary
# =============================================================================

echo
log_info "Done."
log_info "  Copied:         $copied files"
[[ $backed_up   -gt 0 ]] && log_info "  Backed up:      $backed_up files (.bak)"
[[ $overwritten -gt 0 ]] && log_info "  Overwritten:    $overwritten files"
log_info "  Target directory: $TARGET_DIR"

# =============================================================================
# Recommended next steps
# =============================================================================

cat <<EOF

Next steps:
  - Check structure:      ls -la "$TARGET_DIR"
  - Initialize git:       cd "$TARGET_DIR" && git init && git add . && git commit -m "Initial concept import"
  - Validate links:       see validate_links.py (available separately)
EOF
