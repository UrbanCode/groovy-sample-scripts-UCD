//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.1
//This scripts reads an XML file that contains a list of environments, and for each environment it contains a list of properties.
//The script creates environment properties for the environment that it is specified on the command line by the parameters applicationName and environmentName
//or that is specified by resolving the UrbanCode Deploy variables ${p:application.name} and ${p:environment.name}
//Command line usage:
//groovy -cp udclient.jar CreateEnvironmentPropertiesFromXMLFile.groovy https://hostname:port username password xmlFile applicationName EnvironmentName
//Usage from a UrbanCode Deploy Component process requires only the xmlFile as the first parameter 
//You can execute the following from a Shell step in order to have the udclient classes on the classpath:
//${p:agent/GROOVY_HOME}/bin/groovy -cp ${p:agent/AGENT_HOME}/opt/udclient/udclient.jar groovyScript.groovy xmlFile
//Example input xmlFile
/*
<environmentList>
	<environment1>
		<property1>
			value1
		<property1>
		<property2>
			value2
		<property2>
	</environment1>
	<environment2>
		<property1>
			value1.1
		<property1>
		<property2>
			value2.2
		<property2>
	</environment2>
</environmentList>

*/
//Example output
/*
environment1[attributes={}; value=[property1[attributes={}; value=[value1]], property2[attributes={}; value=[value2]]]]
property1
value1
SUCCESS
property2
value2
SUCCESS
*/


import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;
import java.lang.System;
import java.net.URLEncoder;
import org.apache.commons.lang.StringUtils;

class Credentials{
	String serverURL;
	String username;
	String password;
	String xmlFile;
	String applicationName;
	String environmentName;
	
}
	//sets the values of the Credentials class which contains the server url and the username/password
	void initializeParameters(Credentials credentials){
		//if the user provided the input parameters, we're running on the command line
		if (args.size()==6){
			credentials.serverURL = args[0];
			credentials.username = args[1];
			credentials.password = args[2];
			credentials.xmlFile = args[3];
			credentials.applicationName = args[4];
			credentials.environmentName = args[5];
		}
		//otherwise we assume that we are running from a UrbanCode Deploy process
		//we obtain the Server URL from a system property: 
		//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.doc/topics/propertiesreference.html
		//we obtain the Token from the environment variable DS_AUTH_TOKEN
		else{
			try{
			credentials.serverURL="${p:server.url}";
			credentials.username="PasswordIsAuthToken";
			credentials.password=System.getenv("DS_AUTH_TOKEN");
			credentials.xmlFile=args[0];
			credentials.applicationName = "${p:application.name}";
			credentials.environmentName = "${p:environment.name}";
			}//If we do not have the above variables, we might be running from the command line, but the user did not pass the expected parameters
			catch (Exception ex){
				println "Command Line usage: ParseXMLForEnvironment ServerURL username password xmlFile applicationName environmentName\nOnly provide xmlFile if running from a UrbanCode Deploy Component process inside an Application Process"
				org.codehaus.groovy.runtime.StackTraceUtils.sanitize(ex).printStackTrace();
				System.exit(1)
			}
		}
		
	}
    //Initializes an HttpClient that accepts all certificates
	//depends on import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder 
	//included in HttpComponents-Util.jar, which is partly included in udclient.jar
	HttpClient initializeClient(String username,String password){
        HttpClientBuilder builder = new HttpClientBuilder();
		builder.setPreemptiveAuthentication(true);
        builder.setUsername(username);
        builder.setPassword(password);
		//Accept all certificates
        builder.setTrustAllCerts(true);
		//use proxy if defined
        if (!StringUtils.isEmpty(System.getenv("PROXY_HOST")) &&
            StringUtils.isNumeric(System.getenv("PROXY_PORT")))
        {
            builder.setProxyHost(System.getenv("PROXY_HOST"));
            builder.setProxyPort(Integer.valueOf(System.getenv("PROXY_PORT")));
        }

        if (!StringUtils.isEmpty(System.getenv("PROXY_USERNAME")) &&
            !StringUtils.isEmpty(System.getenv("PROXY_PASSWORD")))
        {
            builder.setProxyUsername(System.getenv("PROXY_USERNAME"));
            builder.setProxyPassword(System.getenv("PROXY_PASSWORD"));
        }

		return builder.buildClient();
	}
	
	//Perform a given HttpRequest, assume answer is <= 299 , parse the outcome as JSON. Also release the connection for the Request.
	void setEnvironmentProperty(HttpClient client, String requestURL){
		HttpRequest request = new HttpPut(requestURL);
		//Execute the REST PUT call
		HttpResponse response = client.execute(request); 
		//Check that the call was successful
                int statusCode = response.getStatusLine().getStatusCode(); 
		if ( statusCode > 299 ) {
			println "ERROR : HttpPut to: "+requestURL+ " returned: " +statusCode;
			}
		else{	
			//Convert the InputStream returned by response.getEntity().getContent() to a String 
			println "SUCCESS"

		}
		//Ensure to release the connection
		request.releaseConnection();
	}

	//Main script contents
	Credentials credentials = new Credentials();
	initializeParameters(credentials);
	HttpClient client = initializeClient(credentials.username,credentials.password);

	def xmlFilePath = "C:\\Groovy\\scripts\\EnvironmentList.xml"
    def xmlFile = new File(xmlFilePath)
    def rootNode = new XmlParser().parseText(xmlFile.getText())
	
	assert rootNode.name() == 'environmentList'

	rootNode.children().each{
		if(it.name()==credentials.environmentName){
			println it
			it.children().each{
				println it.name()
				println it.value().getAt(0)
				
				String URL= "/cli/environment/propValue?"+
				"name="+it.name()+
				"&value="+it.value().getAt(0)+
				"&application="+credentials.applicationName+
				"&environment="+credentials.environmentName+
				"&isSecure=false";
				//avoid problems with white spaces or other special characters
				URL = URLEncoder.encode(URL,"UTF-8");
				setEnvironmentProperty(client,credentials.serverURL+URL);
			}
		}
	}
	
	
