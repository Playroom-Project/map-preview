# Tectonic integration boundary

Status: contract only. Implement `ConfigPreviewProvider` per tested Tectonic version family. Read a detached config snapshot, validate the version's actual schema, debounce UI edits and create new preview sessions with updated fingerprints.

Live preview never writes the installed Tectonic configuration. The explicit Apply action validates the expected old content, backs it up and performs an atomic replacement. Bootstrap/config structures must be checked independently for each Tectonic version family.
