package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class AllOtp
{
  private String mobile_num;
  private String ownerId;
  private java.util.Date updated;
  private java.util.Date created;
  private String user_id;
  private String otp_value;
  private String objectId;
  private String opcode;
  private String namak;

  public String getNamak() {
    return namak;
  }

  public void setNamak(String namak) {
    this.namak = namak;
  }

  public String getMobile_num()
  {
    return mobile_num;
  }

  public void setMobile_num( String mobile_num )
  {
    this.mobile_num = mobile_num;
  }

  public String getOwnerId()
  {
    return ownerId;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

  public String getUser_id()
  {
    return user_id;
  }

  public void setUser_id( String user_id )
  {
    this.user_id = user_id;
  }

  public String getOtp_value()
  {
    return otp_value;
  }

  public void setOtp_value( String otp_value )
  {
    this.otp_value = otp_value;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public String getOpcode()
  {
    return opcode;
  }

  public void setOpcode( String opcode )
  {
    this.opcode = opcode;
  }

                                                    
  public AllOtp save()
  {
    return Backendless.Data.of( AllOtp.class ).save( this );
  }

  public Future<AllOtp> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<AllOtp> future = new Future<AllOtp>();
      Backendless.Data.of( AllOtp.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<AllOtp> callback )
  {
    Backendless.Data.of( AllOtp.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( AllOtp.class ).remove( this );
  }

  public Future<Long> removeAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Long> future = new Future<Long>();
      Backendless.Data.of( AllOtp.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( AllOtp.class ).remove( this, callback );
  }

  public static AllOtp findById( String id )
  {
    return Backendless.Data.of( AllOtp.class ).findById( id );
  }

  public static Future<AllOtp> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<AllOtp> future = new Future<AllOtp>();
      Backendless.Data.of( AllOtp.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<AllOtp> callback )
  {
    Backendless.Data.of( AllOtp.class ).findById( id, callback );
  }

  public static AllOtp findFirst()
  {
    return Backendless.Data.of( AllOtp.class ).findFirst();
  }

  public static Future<AllOtp> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<AllOtp> future = new Future<AllOtp>();
      Backendless.Data.of( AllOtp.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<AllOtp> callback )
  {
    Backendless.Data.of( AllOtp.class ).findFirst( callback );
  }

  public static AllOtp findLast()
  {
    return Backendless.Data.of( AllOtp.class ).findLast();
  }

  public static Future<AllOtp> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<AllOtp> future = new Future<AllOtp>();
      Backendless.Data.of( AllOtp.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<AllOtp> callback )
  {
    Backendless.Data.of( AllOtp.class ).findLast( callback );
  }

  public static BackendlessCollection<AllOtp> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( AllOtp.class ).find( query );
  }

  public static Future<BackendlessCollection<AllOtp>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<AllOtp>> future = new Future<BackendlessCollection<AllOtp>>();
      Backendless.Data.of( AllOtp.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<AllOtp>> callback )
  {
    Backendless.Data.of( AllOtp.class ).find( query, callback );
  }
}