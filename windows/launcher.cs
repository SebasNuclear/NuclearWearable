using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

namespace NuclearWearableLauncher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string targetDir = Path.Combine(appData, "NuclearWearable");
                Directory.CreateDirectory(targetDir);

                string runtimeDir = Path.Combine(targetDir, "runtime");
                string javawExe = Path.Combine(runtimeDir, "bin", "javaw.exe");
                string jarPath = Path.Combine(targetDir, "NuclearWearable.jar");

                Assembly asm = Assembly.GetExecutingAssembly();

                // 1. Extraer runtime integrado si no existe (cero requisitos de Java para el usuario final)
                if (!File.Exists(javawExe))
                {
                    string runtimeResource = null;
                    foreach (string name in asm.GetManifestResourceNames())
                    {
                        if (name.IndexOf("runtime.zip", StringComparison.OrdinalIgnoreCase) >= 0)
                        {
                            runtimeResource = name;
                            break;
                        }
                    }

                    if (runtimeResource != null)
                    {
                        string tempZip = Path.Combine(targetDir, "runtime_temp.zip");
                        using (Stream stream = asm.GetManifestResourceStream(runtimeResource))
                        {
                            if (stream != null)
                            {
                                using (FileStream fs = new FileStream(tempZip, FileMode.Create, FileAccess.Write, FileShare.None))
                                {
                                    byte[] buf = new byte[65536];
                                    int read;
                                    while ((read = stream.Read(buf, 0, buf.Length)) > 0)
                                    {
                                        fs.Write(buf, 0, read);
                                    }
                                }
                            }
                        }

                        if (File.Exists(tempZip))
                        {
                            if (Directory.Exists(runtimeDir))
                            {
                                try { Directory.Delete(runtimeDir, true); } catch { }
                            }
                            ZipFile.ExtractToDirectory(tempZip, targetDir);
                            try { File.Delete(tempZip); } catch { }
                        }
                    }
                }

                // 2. Extraer JAR si no existe o difiere en tamaño
                string jarResource = null;
                foreach (string name in asm.GetManifestResourceNames())
                {
                    if (name.IndexOf("NuclearWearable.jar", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        jarResource = name;
                        break;
                    }
                }

                if (jarResource != null)
                {
                    using (Stream stream = asm.GetManifestResourceStream(jarResource))
                    {
                        if (stream != null)
                        {
                            bool needExtract = true;
                            if (File.Exists(jarPath))
                            {
                                FileInfo fi = new FileInfo(jarPath);
                                if (fi.Length == stream.Length)
                                {
                                    needExtract = false;
                                }
                            }

                            if (needExtract)
                            {
                                using (FileStream fs = new FileStream(jarPath, FileMode.Create, FileAccess.Write, FileShare.None))
                                {
                                    byte[] buf = new byte[65536];
                                    int read;
                                    while ((read = stream.Read(buf, 0, buf.Length)) > 0)
                                    {
                                        fs.Write(buf, 0, read);
                                    }
                                }
                            }
                        }
                    }
                }

                // Contingencia en caso de que javaw.exe siga sin existir
                if (!File.Exists(javawExe))
                {
                    javawExe = FindFallbackJava();
                }

                if (!File.Exists(javawExe))
                {
                    MessageBox.Show(
                        "No se pudo inicializar el entorno de ejecución de NuclearWearable.",
                        "NuclearWearable",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error
                    );
                    return;
                }

                // 3. Lanzar la aplicación nativa (Kotlin gestiona el SingleInstanceLock)
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = javawExe;
                psi.Arguments = "--enable-native-access=ALL-UNNAMED -jar \"" + jarPath + "\"";
                psi.WorkingDirectory = targetDir;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;

                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Error al iniciar NuclearWearable:\n" + ex.Message,
                    "NuclearWearable",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        static string FindFallbackJava()
        {
            string jdk26 = @"C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe";
            if (File.Exists(jdk26)) return jdk26;

            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string p = Path.Combine(javaHome, "bin", "javaw.exe");
                if (File.Exists(p)) return p;
            }

            return null;
        }
    }
}
