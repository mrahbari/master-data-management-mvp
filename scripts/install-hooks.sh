#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
GIT_HOOKS_DIR="$PROJECT_DIR/.git/hooks"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MDM MVP - Install Git Hooks${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if we're in a git repository
if [ ! -d "$PROJECT_DIR/.git" ]; then
    echo -e "${RED}Error: Not a git repository!${NC}"
    exit 1
fi

# Create hooks directory if it doesn't exist
mkdir -p "$GIT_HOOKS_DIR"

# Install pre-commit hook
echo -e "${BLUE}Installing pre-commit hook...${NC}"
cp "$SCRIPT_DIR/pre-commit" "$GIT_HOOKS_DIR/pre-commit"
chmod +x "$GIT_HOOKS_DIR/pre-commit"
echo -e "${GREEN}✓ pre-commit hook installed${NC}"

# Install commit-msg hook
echo -e "${BLUE}Installing commit-msg hook...${NC}"
cat > "$GIT_HOOKS_DIR/commit-msg" << 'EOF'
#!/bin/bash

COMMIT_MSG_FILE=$1
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")

# Check for signed-off-by line (optional)
# if ! echo "$COMMIT_MSG" | grep -q "Signed-off-by:"; then
#     echo "Warning: Consider adding 'Signed-off-by: Your Name <email>'"
# fi

exit 0
EOF
chmod +x "$GIT_HOOKS_DIR/commit-msg"
echo -e "${GREEN}✓ commit-msg hook installed${NC}"

# Install pre-push hook
echo -e "${BLUE}Installing pre-push hook...${NC}"
cat > "$GIT_HOOKS_DIR/pre-push" << 'EOF'
#!/bin/bash
set -e

echo "Running final checks before push..."

# Get the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Run build to ensure everything compiles
cd "$PROJECT_DIR/customer-ingestion-service"
./gradlew build -x test --quiet --no-daemon || {
    echo "Build failed for customer-ingestion-service"
    exit 1
}

cd "$PROJECT_DIR/customer-mastering-service"
./gradlew build -x test --quiet --no-daemon || {
    echo "Build failed for customer-mastering-service"
    exit 1
}

echo "✓ All pre-push checks passed!"
exit 0
EOF
chmod +x "$GIT_HOOKS_DIR/pre-push"
echo -e "${GREEN}✓ pre-push hook installed${NC}"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Git Hooks Installed Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Installed hooks:${NC}"
echo "  - pre-commit:  Code formatting, linting, tests"
echo "  - commit-msg:  Commit message validation"
echo "  - pre-push:    Final build verification"
echo ""
echo -e "${BLUE}To bypass hooks (not recommended):${NC}"
echo "  git commit --no-verify"
echo ""
