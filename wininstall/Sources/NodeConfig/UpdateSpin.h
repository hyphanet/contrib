#ifndef _CUPDATESPIN_H_INCLUDED_
#define _CUPDATESPIN_H_INCLUDED_

template <class T> class CUpdateSpin
{
public:
	CUpdateSpin<T> (T &Ref, const T &min, const T &max)
	:
	m_Ref(Ref),
	m_Min(new T(min)),
	m_Max(new T(max))
	{
		//m_Min = new class T(min);
		//m_Max = new class T(max);
	}
	
	virtual ~CUpdateSpin<T> ()

	{
		delete m_Min;
		delete m_Max;
	}

	virtual bool Update(int iDelta)
	{
		if (iDelta == 0)
		{
			return false;
		}

		// because of the way spin controls work, the 'up' arrow
		// yields a negative delta ( why? )
		iDelta = -iDelta; 

		bool bUpdate = false;

		if (m_Ref < *m_Min)
		{
			m_Ref = *m_Min;
			bUpdate = true;
		}
		
		if (m_Ref > *m_Max)
		{
			m_Ref = *m_Max;
			bUpdate = true;
		}
		
		if ( (iDelta > 0) && (m_Ref < *m_Max) )
		{
			bUpdate = true;
			if ( (*m_Max - m_Ref) >= (unsigned int)iDelta )
			{
				// if there is enough 'space' before m_Max to allow us to add
				// the delta to m_Ref without going over m_Max
				m_Ref += iDelta;
			}
			else
			{
				// set to top of range
				m_Ref = *m_Max;
			}
		}
		else if ( (iDelta < 0) && (m_Ref > *m_Min) )
		{
			bUpdate = true;
			if ( (m_Ref - *m_Min) >= (unsigned int)(-iDelta) )
			{
				// if there is enough 'space' after m_Min to allow us to decrement
				// m_Ref without going over m_Min
				m_Ref += iDelta; // remember, iDelta is negative so this is a decrement.
			}
			else
			{
				m_Ref = *m_Min;
			}
		}

		return bUpdate;
	}


private:
	T &m_Ref;
	T * m_Min;
	T * m_Max;
};

#endif // _CUPDATEDSPIN_H_INCLUDED_
