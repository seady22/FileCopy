package ul.sssr.loadbalancer;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
 

import com.amazonaws.*;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

/**
 * @author andy
 *
 *
 *"Lancez une autre instance micro EC2. Créez un simple programme java qui va, en boucle,
prendre un (ou plusieurs) message "val k" sur la queue "QRequest".
    Recherche un hash valide. Le worker fonctionne dans l'idée d'un mineur de BitCoin. 
    Il doit prendre l'entier de l'url X et trouver un nombre aléatoire Y tel que le hash (SHA1) de la chaine "XY" est un tableau de byte
     dont le premier élément est 0. Puis retourner ce Y. 
     Retourne le résultat Y dans la queue "QResponse-k".
     
 */
public class Worker {

	public static void main(String[] args) throws InterruptedException {
		
		/*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (/home/andy/.aws/credentials).
         */
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/home/andy/.aws/credentials), and is in valid format.",
                    e);
		}

		AmazonSQS sqs = new AmazonSQSClient(credentials);
		Region euCentral1 = Region.getRegion(Regions.EU_CENTRAL_1);
		sqs.setRegion(euCentral1);

		String myQRequestUrl = sqs.getQueueUrl("semigambi-QRequest").getQueueUrl();

		while (true) {
			List<Message> msgsSQS = sqs.receiveMessage(
					new ReceiveMessageRequest(myQRequestUrl)
							.withMaxNumberOfMessages(1)).getMessages();

			if (msgsSQS.size() > 0) {
				Message SQSmessage = msgsSQS.get(0);
				String SQSdata = SQSmessage.getBody();
				System.out.println("The Queue message is :  " + SQSdata);

				// Receive the value with the k integer

				String[] value_k= SQSdata.split(" ");
				int n = Integer.parseInt(value_k[0]);
				int k = Integer.parseInt(value_k[1]);

				System.out.println("we have n = " + n);
				System.out.println("we have k = " + k);

				// Receive semigambi-QResponse

				String myResponseUrl = "";
				boolean Queue_is_not_created = true;
				while (Queue_is_not_created) {
					try {
						myResponseUrl = sqs.getQueueUrl("semigambi-QResponse-" + k)
								.getQueueUrl();
						Queue_is_not_created = false;
					} catch (QueueDoesNotExistException qdne) {
						
					}
				}

				int value = fibonacci(n);

				System.out.println("result in the worker :  " + value);
				sqs.sendMessage(new SendMessageRequest(myResponseUrl, "" + value));//send
				sqs.deleteMessage(new DeleteMessageRequest(myQRequestUrl,
						SQSmessage.getReceiptHandle()));//delete
			}
		}
	}
	 public static String getInstance(String algorithm,
             String provider) throws NoSuchAlgorithmException, NoSuchProviderException{
		 MessageDigest md = MessageDigest.getInstance("MD5", "FlexiCore");
		 File file = new File("");
		 StringBuffer buf = new StringBuffer();
		 byte[] buffer = new byte[(int) file.length()];
		 md.update(buffer);
		 byte[] digest = md.digest();
		 return buf.toString();
		 
	 }
	 //computing hash value ....
	 public static byte[] computeHashValue(String algo,String myMsg) {
		 byte[] dig = null;
			 
		 try {
			
	     MessageDigest sha1 = MessageDigest.getInstance(algo);
			
		 sha1.update(myMsg.getBytes());
			
		 dig = sha1.digest();
			
		 System.out.println("algorithme : " + algo);
			
		 } catch (NoSuchAlgorithmException e) {
			
			 e.printStackTrace();
			
		}
			 
		 return dig;
			
		}
	
//fibonacci sequences computing...
	public static int fibonacci(int j) {
		if (j <= 1)
			return 1;
		else
			return fibonacci(j - 2) + fibonacci(j - 1);
	}
	
}