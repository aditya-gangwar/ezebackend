
package in.ezeshop;

import com.backendless.servercode.IBackendlessBootstrap;
import in.ezeshop.utilities.BackendUtils;


public class Bootstrap implements IBackendlessBootstrap
{
            
  @Override
  public void onStart()
  {
    BackendUtils.initAll();
  }
    
  @Override
  public void onStop()
  {
    // add your code here
  }
    
}
        