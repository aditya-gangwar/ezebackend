package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class MerchantIdBatches
{
  private java.util.Date statusTime;
  private String ownerId;
  private java.util.Date updated;
  private String objectId;
  private java.util.Date created;
  private String rangeId;
  private int batchId;
  private String status;
  private String rangeBatchId;

  public String getRangeBatchId() {
    return rangeBatchId;
  }

  public void setRangeBatchId(String rangeBatchId) {
    this.rangeBatchId = rangeBatchId;
  }

  public java.util.Date getStatusTime()
  {
    return statusTime;
  }

  public void setStatusTime( java.util.Date statusTime )
  {
    this.statusTime = statusTime;
  }

  public String getOwnerId()
  {
    return ownerId;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

  public String getRangeId()
  {
    return rangeId;
  }

  public void setRangeId( String rangeId )
  {
    this.rangeId = rangeId;
  }

  public int getBatchId()
  {
    return batchId;
  }

  public void setBatchId(int batchId )
  {
    this.batchId = batchId;
  }

  public String getStatus()
  {
    return status;
  }

  public void setStatus( String status )
  {
    this.status = status;
  }


  public MerchantIdBatches save()
  {
    return Backendless.Data.of( MerchantIdBatches.class ).save( this );
  }

  public Future<MerchantIdBatches> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MerchantIdBatches> future = new Future<MerchantIdBatches>();
      Backendless.Data.of( MerchantIdBatches.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<MerchantIdBatches> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( MerchantIdBatches.class ).remove( this );
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
      Backendless.Data.of( MerchantIdBatches.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).remove( this, callback );
  }

  public static MerchantIdBatches findById( String id )
  {
    return Backendless.Data.of( MerchantIdBatches.class ).findById( id );
  }

  public static Future<MerchantIdBatches> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MerchantIdBatches> future = new Future<MerchantIdBatches>();
      Backendless.Data.of( MerchantIdBatches.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<MerchantIdBatches> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).findById( id, callback );
  }

  public static MerchantIdBatches findFirst()
  {
    return Backendless.Data.of( MerchantIdBatches.class ).findFirst();
  }

  public static Future<MerchantIdBatches> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MerchantIdBatches> future = new Future<MerchantIdBatches>();
      Backendless.Data.of( MerchantIdBatches.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<MerchantIdBatches> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).findFirst( callback );
  }

  public static MerchantIdBatches findLast()
  {
    return Backendless.Data.of( MerchantIdBatches.class ).findLast();
  }

  public static Future<MerchantIdBatches> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MerchantIdBatches> future = new Future<MerchantIdBatches>();
      Backendless.Data.of( MerchantIdBatches.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<MerchantIdBatches> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).findLast( callback );
  }

  public static BackendlessCollection<MerchantIdBatches> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( MerchantIdBatches.class ).find( query );
  }

  public static Future<BackendlessCollection<MerchantIdBatches>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<MerchantIdBatches>> future = new Future<BackendlessCollection<MerchantIdBatches>>();
      Backendless.Data.of( MerchantIdBatches.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<MerchantIdBatches>> callback )
  {
    Backendless.Data.of( MerchantIdBatches.class ).find( query, callback );
  }
}