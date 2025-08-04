import groovy.json.JsonBuilder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

def appProcessName = "UNINSTALL FROM INVENTORY"
def app = "${p:application.id}"
String envProp = "${p:GetEnvironmentProperty/lowerEnvironments}"
String versionStatuses = "${p:GetVersionStatus/versionStatuses}"

def envProps = envProp?.split(",") ?: []
def versionStatus = versionStatuses?.split(",") ?: [] 
def envList = envProps.toList().intersect(versionStatus.toList())

envList.each { envName ->
    if(!envName.isEmpty()) {
    println "Running process on ${envName}" 
    runProcessOnEnvironment(envName, appProcessName, app)
    }
}

private void runProcessOnEnvironment(String env, String applicationProcessName, String applicationName) {
    def trustAllCerts = [
            new X509TrustManager() {
                X509Certificate[] getAcceptedIssuers() { null }

                void checkClientTrusted(X509Certificate[] certs, String authType) {}

                void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
    ] as TrustManager[]

    def sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, null)
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier { hostname, session -> true }

    def targetUrl = new URL("https://10.134.119.177:8443/cli/applicationProcessRequest/request")
    // Insecure HTTPS endpoint
    def username = "admin"
    def password = "admin"

    def payloadMap = [
            "application"       : applicationName,
            "applicationProcess": applicationProcessName,
            "environment"       : env,
            "onlyChanged"       : false,
            "versions"          : [
                    [
                            "component": "${p:component.name}",
                            "version"  : "${p:version.name}"
                    ]
            ]
    ]
    def jsonPayload = new JsonBuilder(payloadMap).toPrettyString()

    println "Sending Payload:\n${jsonPayload}"
    println "--------------------"

    HttpsURLConnection connection = (HttpsURLConnection) targetUrl.openConnection()

    try {
        def authString = "${username}:${password}".getBytes("UTF-8").encodeBase64().toString()
        connection.setRequestProperty("Authorization", "Basic ${authString}")
        connection.setRequestMethod("PUT")
        connection.setDoOutput(true)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        connection.getOutputStream().withStream { it.write(jsonPayload.getBytes("UTF-8")) }

        def responseCode = connection.responseCode
        println "Response Code: ${responseCode}"
        def responseBody = connection.inputStream.getText("UTF-8")
        println "Response Body: ${responseBody}"

    } catch (Exception e) {
        println "An error occurred: ${e.message}"
        def errorBody = connection.errorStream?.getText("UTF-8")
        if (errorBody) println "Error Body: ${errorBody}"
    } finally {
        connection.disconnect()
    }
}





