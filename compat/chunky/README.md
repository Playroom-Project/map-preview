# Chunky integration boundary

Status: contract only. A supported public Chunky API may implement the `PregenBridge` boundary. It must preserve area identity, pause/cancel/drain, server-thread ownership, native save barriers and checkpoint semantics.

Do not create a second competing worldgen pool or inspect Chunky's private internals. Test API behavior against each supported loader/version combination.
