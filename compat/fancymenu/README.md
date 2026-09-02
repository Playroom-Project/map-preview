# FancyMenu integration boundary

Status: contract only. Bind version-checked public FancyMenu actions to `PreviewUiApi` and `PreviewUiState`. Keep the map canvas composable and screen/widget identity stable.

The mod must work without FancyMenu. Test the actual native screens against the chosen FancyMenu build and its customization blocklist before marking this integration supported.
