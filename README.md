# BehaviorArchitectures
FTC Library for Behavior Architectures (FSM, Command based system, and behavior trees)

Modules:

- `util` — shared support: `Blackboard` (typed shared state) and `Logger` (log fan-out).
- `behaviorTrees` — behavior-tree nodes.
- `cmdlib` — command-based framework (Ivy-style, WPILib v2 lifecycle).
- `fsm` — switch/case state machines, tick-driven via `update()`.
- `ftc` — OpMode integration for the modules above: `ArchitectureManager` (one entry point for trees, machines, and commands, holding the tree, state-machine, and command builders), `ArchitectureConverter` (convert between architectures), and `ArchitecturalOpMode` (a `LinearOpMode` base that runs everything through one manager).
