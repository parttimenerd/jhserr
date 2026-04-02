# Change Log

All notable changes to the hs_err syntax extension will be documented in this file.

Follows [Keep a Changelog](http://keepachangelog.com/).

## [0.1.2]

### Fixed
- Screenshot in README.md was not displayed

## [0.1.1]

### Changed
- Better file detection

## [0.1.0]

### Added
- Initial release — TextMate grammar derived from YAML spec (1604 lines)
- Auto-detection of `hs_err_pid*.log`, `hs_err*.log`, `hserr*.log`, and `replay_pid*.log`
- Section folding at `--- T H R E A D ---` / `--- P R O C E S S ---` / etc. banners
- Word selection includes hex addresses and Java identifiers
- 45+ contexts covering every hs_err section
- 500+ keywords (signals, thread types, register names across 4 architectures)
