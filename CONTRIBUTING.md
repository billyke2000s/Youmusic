# Contributing to YouMusic

Thanks for helping out. This is a small hobby project, so the process is simple.

## Reporting a problem

Open an [issue](https://github.com/billyke2000s/Youmusic/issues) and include:

- Your phone model and Android version
- What you did (for example: pasted a link, pressed Play, locked the screen)
- What happened, and what you expected to happen
- The link you used, if it is relevant

Background playback problems are the most useful reports, because behaviour varies
a lot between phone makers.

## Making a change

1. Fork the repo and create a branch.
2. Make your change. Keep to the existing style:
   - Java only, no external libraries unless there is a strong reason.
   - UI is built in code, not XML layouts.
   - Comment *why* something is done when it is not obvious, especially for the
     background-playback workarounds.
3. Push. The **Build YouMusic APK** workflow must pass.
4. Open a pull request describing what you changed and how you tested it.
5. Add a line to `CHANGELOG.md`.

## Building

See [Building it yourself](README.md#building-it-yourself) in the README.
