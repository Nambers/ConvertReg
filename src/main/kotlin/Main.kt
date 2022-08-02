import org.ini4j.Profile
import org.ini4j.Wini
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.system.exitProcess

fun getKey(str: String): String {
    val re = str.substringAfter("SOFTWARE\\").substringBefore("\\")
    if (re == "WOW6432Node") {
        if (str.substringAfter("SOFTWARE\\WOW6432Node\\").substringBefore("\\") == "Classes") {
            return "WOW6432Node\\Classes\\" + str.substringAfter("SOFTWARE\\WOW6432Node\\Classes\\")
                .substringBefore("\\")
        }
        return "WOW6432Node\\" + str.substringAfter("SOFTWARE\\WOW6432Node\\").substringBefore("\\")
    }
    if (re == "Classes") {
        return "Classes\\" + str.substringAfter("SOFTWARE\\Classes\\").substringBefore("\\")
    }
    return re
}

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Err arguments(should be: <old reg file> <current reg file> <generated reg file path>)")
        exitProcess(1)
    }

    // prepare
    var log = ""
    val ini = Wini(File(args[0]))
    val newIni = Wini(File(args[1]))
    val f = File(args[2])
    val logFile = File("./ConvertReg.log")
    if (logFile.exists()) logFile.delete()
    logFile.createNewFile()
    val groups = mutableMapOf<String, MutableList<Pair<String, Profile.Section>>>()

    // cut name
    println("cut name")
    ini.forEach {
        val key = getKey(it.key)
        println(it.key + " -> " + key)
        groups[key]?.add(Pair(it.key, it.value)) ?: run {
            groups[key] = mutableListOf(Pair(it.key, it.value))
        }
    }
    println(groups.keys.size)

    // remove duplicate with current reg
    println("Remove duplicates")
    newIni.forEach {
        val key = getKey(it.key)
        if (key in groups.keys) {
            log += "keyGroup(${it.key.substringBefore(key) + key}) will not be saved because duplicate with current reg\n"
            groups.remove(key)
        }
    }
    println(groups.keys.size)
    log += "\n===============\n\n"

    // check path item from old reg exist
    println("check path existed")
    val pathRe =
        Regex("^[a-z]:\\\\(?:[^\\\\\\/:*?\"<>|\\r\\n]+\\\\)*[^\\\\\\/:*?\"<>|\\r\\n]*\$", RegexOption.IGNORE_CASE)
    if (f.exists()) f.delete()
    f.createNewFile()
    // result ini
    val newNewIni = Wini()
    var count = 0
    groups.forEach a@{ section ->
        println("Checking " + section.key)
        section.value.forEach {
            println("check each path key for " + it.first)
            it.second.keys.forEach key@{ key ->
                // fix bug of Ini4j
                if (it.second[key] == null) return@key
                println("check key $key : ${it.second[key]!!.replace("\"", "").replace("\\\\", "\\")}")
                if (it.second[key]!!.replace("\"", "").replace("\\\\", "\\").contains(pathRe)) {
                    val ff = File(it.second[key]!!.replace("\"", "", true))
                    println("check path: ${it.second[key]!!}")
                    if (!ff.exists()) {
                        println("key($key : " + ff.absolutePath + "don't exist")
                        log += "key(${it.first}) will not be saved because item(key=$key, path=${ff.absolutePath}) don't exist\n"
                        return@a
                    }
                }
            }
        }
        count++
        section.value.forEach {
            println("Write " + it.first)
            newNewIni[it.first] = it.second
        }
    }
    log += "\n===============\n\n"

    // done
    println("Write $count software (include ${newNewIni.size} items) to reg file(${f.absolutePath})")
    println()
    newNewIni.keys.forEach {
        log += "key($it) will be saved\n"
    }
    val buff = ByteArrayOutputStream()
    newNewIni.store(buff)
    f.writeText("Windows Registry Editor Version 5.00\n\n$buff")
    logFile.writeText(log, Charset.defaultCharset())
    println("log file generated in ${logFile.absolutePath}")
    println("Done!")
}