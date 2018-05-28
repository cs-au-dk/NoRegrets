package distilling.server.utils

import com.google.javascript.jscomp._

object PrettyJS {

  def prettify(source: String): String = {
    val compiler = new com.google.javascript.jscomp.Compiler()
    val file = SourceFile.fromCode("filename", source)
    val p = compiler.parse(file)
    new CodePrinter.Builder(p).setPrettyPrint(true).setLineBreak(true).build()
  }

}
