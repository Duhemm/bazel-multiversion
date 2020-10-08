package multideps.diagnostics

import moped.reporters.Diagnostic
import moped.reporters.ErrorSeverity
import moped.reporters.Position
import coursier.core.Module
import coursier.core.Dependency
import MultidepsEnrichments._

class ConflictingTransitiveDependencyDiagnostic(
    module: Module,
    transitiveVersions: List[String],
    declaredVersions: List[String],
    rootDependencies: List[Dependency],
    pos: Position
) extends Diagnostic(ErrorSeverity, "", pos) {
  private val roots = rootDependencies.filterNot(_.module == module)
  override def message: String = {
    def tokenize(x: Any): Iterator[fansi.Str] =
      pprint.PPrinter.BlackWhite.tokenize(x)
    def pretty(xs: Iterable[Any]): String =
      if (xs.isEmpty) ""
      else if (xs.size == 1) tokenize(xs.head).mkString(" ", "", "")
      else xs.map(tokenize(_).mkString).mkString(" ", ", ", "")
    val toFix =
      if (pos.isNone)
        s"add 'forceVersions' to the root dependencies OR create a new root dependency for the module '${module.repr}'."
      else
        "add 'forceVersions' to the root dependencies OR add 'crossVersions' to the transitive dependency."
    s"""transitive dependency '${module.repr}' has conflicting versions.
       |  resolved versions:${pretty(transitiveVersions)}
       |  declared versions:${pretty(declaredVersions)}
       |  root dependencies:${pretty(roots.map(_.repr))}
       |To fix this problem, $toFix
       |""".stripMargin.trim
  }
}
