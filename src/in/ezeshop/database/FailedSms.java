package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class FailedSms
{
  private java.util.Date updated;
  private String ownerId;
  private String text;
  private java.util.Date created;
  private String objectId;
  private String recipients;
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getOwnerId()
  {
    return ownerId;
  }

  public String getText()
  {
    return text;
  }

  public void setText( String text )
  {
    this.text = text;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public String getRecipients()
  {
    return recipients;
  }

  public void setRecipients( String recipients )
  {
    this.recipients = recipients;
  }

                                                    
  public FailedSms save()
  {
    return Backendless.Data.of( FailedSms.class ).save( this );
  }

  public Future<FailedSms> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<FailedSms> future = new Future<FailedSms>();
      Backendless.Data.of( FailedSms.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<FailedSms> callback )
  {
    Backendless.Data.of( FailedSms.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( FailedSms.class ).remove( this );
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
      Backendless.Data.of( FailedSms.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( FailedSms.class ).remove( this, callback );
  }

  public static FailedSms findById( String id )
  {
    return Backendless.Data.of( FailedSms.class ).findById( id );
  }

  public static Future<FailedSms> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<FailedSms> future = new Future<FailedSms>();
      Backendless.Data.of( FailedSms.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<FailedSms> callback )
  {
    Backendless.Data.of( FailedSms.class ).findById( id, callback );
  }

  public static FailedSms findFirst()
  {
    return Backendless.Data.of( FailedSms.class ).findFirst();
  }

  public static Future<FailedSms> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<FailedSms> future = new Future<FailedSms>();
      Backendless.Data.of( FailedSms.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<FailedSms> callback )
  {
    Backendless.Data.of( FailedSms.class ).findFirst( callback );
  }

  public static FailedSms findLast()
  {
    return Backendless.Data.of( FailedSms.class ).findLast();
  }

  public static Future<FailedSms> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<FailedSms> future = new Future<FailedSms>();
      Backendless.Data.of( FailedSms.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<FailedSms> callback )
  {
    Backendless.Data.of( FailedSms.class ).findLast( callback );
  }

  public static BackendlessCollection<FailedSms> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( FailedSms.class ).find( query );
  }

  public static Future<BackendlessCollection<FailedSms>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<FailedSms>> future = new Future<BackendlessCollection<FailedSms>>();
      Backendless.Data.of( FailedSms.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<FailedSms>> callback )
  {
    Backendless.Data.of( FailedSms.class ).find( query, callback );
  }
}