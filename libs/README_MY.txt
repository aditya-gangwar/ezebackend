The backendless.jar (size 935 KB) used here is the one taken from our backendless installation on linux.

Actually I was getting 'java.lang.NoSuchMethodError' exception in backend, while trying to send push notification. 
This was happening as the backendless.jar used while compiling API services was later version - than the one available in backendless installation.

So, when the API code was running in backend - there was run time mismatch found in some function.

So, the jar used while compiling API services - is replaced with the one from backend.

