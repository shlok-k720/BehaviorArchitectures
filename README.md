# BehaviorArchitectures
FTC Library for Behavior Architectures (FSM, Command based system, and behavior trees)

Modules:

- Shared state and logging (`Blackboard`, `Logger`) live in FTCUtils (`com.rmsftc.utils.*`) — the old `util` module was removed in 0.2.1.
- `behaviorTrees` — behavior-tree nodes.
- `cmdlib` — command-based framework (Ivy-style, WPILib v2 lifecycle).
- `fsm` — switch/case state machines, tick-driven via `update()`.
- `ftc` — OpMode integration for the modules above: `ArchitectureManager` (one entry point for trees, machines, and commands, holding the tree, state-machine, and command builders), `ArchitectureConverter` (convert between architectures), and `ArchitecturalOpMode` (a `LinearOpMode` base that runs everything through one manager).
