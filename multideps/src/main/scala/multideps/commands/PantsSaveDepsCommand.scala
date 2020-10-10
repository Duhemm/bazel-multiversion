package multideps.commands

import java.nio.file.Files
import java.nio.file.Path

import multideps.configs.ThirdpartyConfig
import multideps.diagnostics.MultidepsEnrichments._

import moped.annotations.CommandName
import moped.annotations.Inline
import moped.annotations.PositionalArguments
import moped.cli.Command
import moped.cli.CommandParser
import moped.json.DecodingResult
import moped.json.ErrorResult
import moped.json.ValueResult
import moped.reporters.Diagnostic
import moped.reporters.Input
import os.Inherit
import os.Shellable
import moped.parsers.JsonParser
import java.nio.charset.StandardCharsets

@CommandName("pants-save")
final case class PantsSaveDepsCommand(
    export: Boolean = true,
    @PositionalArguments()
    pantsTargets: List[String] = List("3rdparty/jvm::"),
    cwd: Option[Path] = None,
    @Inline
    save: SaveDepsCommand = SaveDepsCommand.default
) extends Command {
  def app = save.app
  def workingDirectory = cwd.getOrElse(app.env.workingDirectory)

  // NOTE(olafur): I tried to use the --output-path flag but it didn't work for
  // some reason. Hardcoding this flag for now.
  def outputPath: Path =
    workingDirectory.resolve(".pants.d").resolve("bazel-multideps.json")
  def inputPath: Path =
    workingDirectory.resolve("3rdparty.yaml")

  def run(): Int = app.complete(runResult())
  def runResult(): DecodingResult[Unit] = {
    for {
      _ <- runPantsExport()
      thirdparty <- runPantsImport()
      save <- save.runResult(thirdparty)
    } yield save
  }

  def runPantsImport(): DecodingResult[ThirdpartyConfig] = {
    if (!Files.isRegularFile(outputPath)) {
      ErrorResult(
        Diagnostic.error(
          s"no such file: $outputPath\n" +
            s"\tTo fix this problem, run Pants bazel-multideps again."
        )
      )
    } else {
      for {
        json <- JsonParser.parse(Input.path(outputPath))
        _ = Files.write(
          outputPath,
          json.toDoc.render(120).getBytes(StandardCharsets.UTF_8)
        )
        thirdparty <- ThirdpartyConfig.parseJson(Input.path(outputPath))
      } yield thirdparty
    }
  }
  def runPantsExport(): DecodingResult[Unit] = {
    if (export) {
      val binary = workingDirectory.resolve("pants")
      val command = List[String](
        binary.toString(),
        "bazel-multideps"
      ) ++ pantsTargets
      val commandString = command.mkString(" ")
      app.reporter.info(commandString)
      val proc = app
        .process(command.map(Shellable.StringShellable(_)): _*)
        .call(cwd = workingDirectory, stdout = Inherit)
      if (proc.exitCode != 0) {
        ErrorResult(
          Diagnostic.error(
            s"Pants exited with code '${proc.exitCode}'. " +
              s"To reproduce this error, run the command:\n\t$commandString"
          )
        )
      } else {
        ValueResult(())
      }
    } else {
      ValueResult(())
    }
  }
}

object PantsSaveDepsCommand {
  val default: PantsSaveDepsCommand = PantsSaveDepsCommand()
  implicit val parser: CommandParser[PantsSaveDepsCommand] =
    CommandParser.derive(default)
}
