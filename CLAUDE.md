# Horizon Nav: notes for Claude

The project layout and the rules for working with the owner are kept in one place so they never drift apart:

@.github/copilot-instructions.md

Follow every rule in that file. A few extra notes for Claude:

- **Pushing:** `git -c push.negotiate=true push origin HEAD:main`, and only after the owner says yes.
- **GitHub:** use `gh api` (REST). GraphQL is blocked and `gh release download` fails.
- **Reporters' screenshots** can't be downloaded; ask the owner to attach them.
- **Nothing can be tested here.** Say plainly what has not been tested on a real phone, head unit or Android Auto.
- **Before delivering,** think through problems on a slow head unit (freezes, crashes, memory) rather than leaving the owner to find them.
- **Keep replies short and in plain words:** what changed, and what the owner needs to do.
