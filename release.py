#!/usr/bin/env python3
"""
Bump version and deploy jhserr library.

This script:
1. Reads the current version from pom.xml
2. Bumps the version (major/minor/patch)
3. Updates pom.xml and README.md with new version
4. Runs tests
5. Builds the package
6. Optionally deploys to Maven Central
7. Creates a git tag and commits the changes
"""

import re
import sys
import subprocess
import argparse
from pathlib import Path
from typing import Tuple


class VersionBumper:
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.pom_xml = project_root / "pom.xml"
        self.readme = project_root / "README.md"
        self.changelog = project_root / "CHANGELOG.md"
        self.backup_dir = project_root / ".release-backup"
        self.backups_created = False

    def get_current_version(self) -> str:
        """Extract current version from pom.xml"""
        pom_content = self.pom_xml.read_text()
        match = re.search(r'<version>([\d.]+)</version>', pom_content)
        if not match:
            raise ValueError("Could not find version in pom.xml")
        return match.group(1)

    def parse_version(self, version: str) -> Tuple[int, int, int]:
        """Parse version string into (major, minor, patch)"""
        parts = version.split('.')
        if len(parts) == 2:
            return (int(parts[0]), int(parts[1]), 0)
        elif len(parts) == 3:
            return tuple(map(int, parts))
        else:
            raise ValueError(f"Invalid version format: {version}. Expected format: X.Y or X.Y.Z")

    def bump_minor(self, version: str) -> str:
        major, minor, patch = self.parse_version(version)
        return f"{major}.{minor + 1}.0"

    def bump_major(self, version: str) -> str:
        major, minor, patch = self.parse_version(version)
        return f"{major + 1}.0.0"

    def bump_patch(self, version: str) -> str:
        major, minor, patch = self.parse_version(version)
        return f"{major}.{minor}.{patch + 1}"

    def update_pom_xml(self, old_version: str, new_version: str):
        content = self.pom_xml.read_text()
        content = content.replace(
            f'<version>{old_version}</version>',
            f'<version>{new_version}</version>',
            1
        )
        self.pom_xml.write_text(content)
        print(f"✓ Updated pom.xml: {old_version} -> {new_version}")

    def update_readme(self, old_version: str, new_version: str):
        content = self.readme.read_text()
        content = content.replace(
            f'<version>{old_version}</version>',
            f'<version>{new_version}</version>'
        )
        content = content.replace(
            f"'me.bechberger:jhserr:{old_version}'",
            f"'me.bechberger:jhserr:{new_version}'"
        )
        self.readme.write_text(content)
        print(f"✓ Updated README.md: {old_version} -> {new_version}")

    def get_changelog_entry(self, version: str) -> str:
        if not self.changelog.exists():
            return ""
        content = self.changelog.read_text()
        unreleased_match = re.search(
            r'## \[Unreleased\]\s*\n(.*?)(?=\n## \[|$)',
            content,
            re.DOTALL
        )
        if unreleased_match:
            return unreleased_match.group(1).strip()
        return ""

    def get_version_changelog_entry(self, version: str) -> str:
        if not self.changelog.exists():
            return ""
        content = self.changelog.read_text()
        version_pattern = rf'## \[{re.escape(version)}\][^\n]*\n(.*?)(?=\n## \[|$)'
        version_match = re.search(version_pattern, content, re.DOTALL)
        if version_match:
            entry = version_match.group(1).strip()
            lines = []
            header = None
            for line in entry.split('\n'):
                if line.startswith('###'):
                    header = line
                    continue
                if line.strip():
                    if header:
                        lines.append(header)
                        header = None
                    lines.append(line)
            return '\n'.join(lines) if lines else ""
        return ""

    def validate_changelog(self, version: str) -> bool:
        entry = self.get_changelog_entry(version)
        if not entry or len(entry) < 20:
            print("\n❌ ERROR: CHANGELOG.md must have content in [Unreleased] section")
            print("\nPlease add your changes to CHANGELOG.md under [Unreleased]:")
            print("  ### Added")
            print("  - New feature 1")
            return False
        return True

    def update_changelog(self, version: str):
        if not self.changelog.exists():
            print("⚠ No CHANGELOG.md found, skipping")
            return
        content = self.changelog.read_text()
        from datetime import datetime
        today = datetime.now().strftime('%Y-%m-%d')
        unreleased_pattern = r'## \[Unreleased\]'
        version_section = f'## [Unreleased]\n\n### Added\n### Changed\n### Deprecated\n### Removed\n### Fixed\n### Security\n\n## [{version}] - {today}'
        content = re.sub(unreleased_pattern, version_section, content, count=1)
        self.changelog.write_text(content)
        print(f"✓ Updated CHANGELOG.md for version {version}")

    def create_github_release(self, version: str):
        tag = f'v{version}'
        try:
            subprocess.run(['gh', '--version'], capture_output=True, check=True)
        except (subprocess.CalledProcessError, FileNotFoundError):
            print("⚠ GitHub CLI (gh) not found. Skipping GitHub release creation.")
            return
        try:
            result = subprocess.run(['gh', 'auth', 'status'], capture_output=True, text=True)
            if result.returncode != 0:
                print("⚠ GitHub CLI not authenticated. Run: gh auth login")
                return
        except:
            print("⚠ Could not check GitHub CLI auth status")
            return

        changelog_entry = self.get_version_changelog_entry(version)
        if not changelog_entry:
            changelog_entry = f"Release {version}\n\nSee [CHANGELOG.md](https://github.com/parttimenerd/jhserr/blob/main/CHANGELOG.md) for details."

        release_notes = f"""# Release {version}

{changelog_entry}

## Installation

### Maven
```xml
<dependency>
    <groupId>me.bechberger</groupId>
    <artifactId>jhserr</artifactId>
    <version>{version}</version>
</dependency>
```
"""
        notes_file = self.project_root / '.release-notes.md'
        notes_file.write_text(release_notes)

        try:
            jar_path = self.project_root / 'target' / 'jhserr.jar'
            assets = []
            if jar_path.exists():
                assets.append(str(jar_path) + '#jhserr.jar')

            cmd = ['gh', 'release', 'create', tag,
                   '--title', f'Release {version}',
                   '--notes-file', str(notes_file)] + assets
            self.run_command(cmd, f"Creating GitHub release {tag}")
        finally:
            if notes_file.exists():
                notes_file.unlink()

    def create_backups(self):
        import shutil
        self.backup_dir.mkdir(exist_ok=True)
        for file in [self.pom_xml, self.readme, self.changelog]:
            if file.exists():
                shutil.copy2(file, self.backup_dir / file.name)
        self.backups_created = True
        print("✓ Created backups of files")

    def restore_backups(self):
        import shutil
        if not self.backups_created or not self.backup_dir.exists():
            return
        print("\n⚠️  Restoring files from backup...")
        for name in ["pom.xml", "README.md", "CHANGELOG.md"]:
            backup_file = self.backup_dir / name
            if backup_file.exists():
                shutil.copy2(backup_file, self.project_root / name)
                print(f"  ✓ Restored {name}")
        print("✓ All files restored from backup")

    def cleanup_backups(self):
        import shutil
        if self.backup_dir.exists():
            shutil.rmtree(self.backup_dir)
            print("✓ Cleaned up backups")

    def run_command(self, cmd: list, description: str, check=True) -> subprocess.CompletedProcess:
        print(f"\n→ {description}...")
        print(f"  $ {' '.join(cmd)}")
        result = subprocess.run(cmd, cwd=self.project_root, capture_output=True, text=True)
        if result.returncode != 0 and check:
            print(f"✗ Failed: {description}")
            print(f"  stdout: {result.stdout}")
            print(f"  stderr: {result.stderr}")
            self.restore_backups()
            print("\n❌ Release failed. All changes have been reverted.")
            sys.exit(1)
        print(f"✓ {description}")
        return result

    def run_tests(self):
        self.run_command(['mvn', 'clean', 'test'], "Running tests")

    def build_package(self):
        self.run_command(['mvn', 'clean', 'package'], "Building package")

    def deploy_release(self):
        self.run_command(['mvn', 'clean', 'deploy', '-P', 'release'], "Deploying to Maven Central")

    def git_commit(self, version: str):
        self.run_command(['git', 'add', 'pom.xml', 'README.md', 'CHANGELOG.md'], "Staging files")
        self.run_command(['git', 'commit', '-m', f'Bump version to {version}'], "Committing changes")

    def git_tag(self, version: str):
        tag = f'v{version}'
        self.run_command(['git', 'tag', '-a', tag, '-m', f'Release {version}'], f"Creating tag {tag}")

    def git_push(self, push_tags: bool = True):
        self.run_command(['git', 'push'], "Pushing commits")
        if push_tags:
            self.run_command(['git', 'push', '--tags'], "Pushing tags")


def main():
    parser = argparse.ArgumentParser(
        description='Bump version and deploy jhserr library',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  ./release.py              # Minor release (default)
  ./release.py --patch      # Patch release
  ./release.py --major      # Major release
  ./release.py --dry-run    # Show what would happen
  ./release.py --no-deploy  # Build only, no Maven Central deploy
        '''
    )
    parser.add_argument('--major', action='store_true', help='Bump major version (x.0.0)')
    parser.add_argument('--minor', action='store_true', help='Bump minor version (0.x.0) [default]')
    parser.add_argument('--patch', action='store_true', help='Bump patch version (0.0.x)')
    parser.add_argument('--no-deploy', action='store_true', help='Skip deployment to Maven Central')
    parser.add_argument('--no-github-release', action='store_true', help='Skip GitHub release creation')
    parser.add_argument('--no-push', action='store_true', help='Skip pushing to git remote')
    parser.add_argument('--skip-tests', action='store_true', help='Skip running tests')
    parser.add_argument('--dry-run', action='store_true', help='Show what would happen without making changes')

    args = parser.parse_args()
    project_root = Path(__file__).resolve().parent
    bumper = VersionBumper(project_root)

    current_version = bumper.get_current_version()
    print(f"Current version: {current_version}")

    if args.major:
        new_version = bumper.bump_major(current_version)
        bump_type = "major"
    elif args.patch:
        new_version = bumper.bump_patch(current_version)
        bump_type = "patch"
    else:
        new_version = bumper.bump_minor(current_version)
        bump_type = "minor"

    print(f"New version ({bump_type}): {new_version}")

    do_deploy = not args.no_deploy
    do_github_release = not args.no_github_release
    do_push = not args.no_push

    if not args.dry_run:
        if not bumper.validate_changelog(new_version):
            sys.exit(1)

    if args.dry_run:
        print("\n=== DRY RUN MODE ===")
        print(f"\n  pom.xml:    {current_version} -> {new_version}")
        print(f"  README.md:  {current_version} -> {new_version}")
        print(f"  CHANGELOG:  [Unreleased] -> [{new_version}]")
        print("\n✓ No changes made (dry run)")
        return

    response = input(f"\nBump {current_version} -> {new_version} and release? [y/N] ")
    if response.lower() not in ['y', 'yes']:
        print("Aborted.")
        sys.exit(0)

    try:
        bumper.create_backups()

        print("\n=== Updating version files ===")
        bumper.update_pom_xml(current_version, new_version)
        bumper.update_readme(current_version, new_version)
        bumper.update_changelog(new_version)

        if not args.skip_tests:
            print("\n=== Running tests ===")
            bumper.run_tests()

        print("\n=== Building package ===")
        bumper.build_package()

        if do_deploy:
            print("\n=== Deploying to Maven Central ===")
            response = input("\nReady to deploy? [y/N] ")
            if response.lower() not in ['y', 'yes']:
                print("Skipping deployment.")
                do_deploy = False
            else:
                bumper.deploy_release()

        print("\n=== Git operations ===")
        bumper.git_commit(new_version)
        bumper.git_tag(new_version)

        if do_push:
            bumper.git_push(push_tags=True)

        if do_github_release:
            print("\n=== Creating GitHub release ===")
            bumper.create_github_release(new_version)

        bumper.cleanup_backups()

    except KeyboardInterrupt:
        print("\n\n⚠️  Release interrupted by user")
        bumper.restore_backups()
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ Unexpected error: {e}")
        bumper.restore_backups()
        raise

    print("\n" + "=" * 60)
    print(f"✓ Successfully released version {new_version}")
    print("=" * 60)
    print(f"\n  ✓ Version bumped: {current_version} -> {new_version}")
    print(f"  ✓ CHANGELOG.md updated")
    print(f"  {'✓' if not args.skip_tests else '⊘'} Tests")
    print(f"  ✓ Package built")
    print(f"  {'✓' if do_deploy else '⊘'} Maven Central deployment")
    print(f"  {'✓' if do_push else '⊘'} Git push")
    print(f"  {'✓' if do_github_release else '⊘'} GitHub release")

    if do_deploy:
        print(f"\n📦 Maven Central: https://central.sonatype.com/artifact/me.bechberger/jhserr/{new_version}")
    if do_github_release:
        print(f"📦 GitHub: https://github.com/parttimenerd/jhserr/releases/tag/v{new_version}")


if __name__ == '__main__':
    main()
