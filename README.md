# BehaviorArchitectures
FTC Library for Behavior Architectures (FSM, Command based system, and behavior trees)

Modules:

- `util` — shared support: `Blackboard` (typed shared state) and `Logger` (log fan-out).
- `behaviorTrees` — behavior-tree nodes and builders.
- `cmdlib` — command-based framework (Ivy-style, WPILib v2 lifecycle).
- `fsm` — switch/case state machines, tick-driven via `update()`.
- `ftc` — OpMode integration (`TreesManager` and friends) for the modules above.
